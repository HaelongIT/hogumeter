"""컨테이너 엔트리포인트 — 레지스트리·fetcher·스케줄러·적재기를 조립한다.

두 개의 독립된 스위치가 있다:

1. **실 네트워크 폴링**은 기본 비활성. CLAUDE.md의 정지조건("실사이트 크롤링")을 산문이 아니라
   환경변수 게이트로 강제한다 — `COLLECTOR_ALLOW_NETWORK=1` 없이는 opener를 한 번도 호출하지 않는다.
2. **DB 적재**는 `DB_HOST`가 있을 때만. 없으면 수집 결과를 로그 이벤트로만 남기고 그 사실을 알린다.

커서(사이트별 폴링 상태)는 아직 영속화하지 않는다 — 차단당한 사이트의 재개 경로가 미결이라
(`decisions-needed` D-3) 지금 디스크에 남기면 영구 중지가 된다. 재시작하면 상태가 초기화된다.

**수명 계약**(compose의 `restart: on-failure`가 이 규약에 의존한다):

- opt-in 없음 → `refused` 이벤트 1줄, **exit 0**. 재시작하지 않는다(정상 종료다).
- opt-in 있음 → 상주 루프. `SIGTERM`을 받으면 **현재 사이클을 마치고** `stopped` 이벤트 후 exit 0.
- 적재 연속 실패 `SINK_FAILURE_LIMIT`회 → `giving_up` 이벤트 후 **exit 1**. 수집만 계속 도는데
  아무것도 저장되지 않는 상태가 가장 나쁘다 — 조용히 돌지 않고 죽어서 재시작·사람에게 넘긴다.
"""

from __future__ import annotations

import os
import signal
import threading
import time
from datetime import datetime, timedelta, timezone

from .db.raw_deal_sink import RawDealSink, connect_from_env
from .observability import counters, event
from .pipeline.ingest import to_raw_records
from .scheduler.drift import DriftHistory, DriftPolicy, observe
from .scheduler.fetcher import HttpFetcher, RobotsGate, urllib_opener
from .scheduler.loop import run_cycle
from .scheduler.policy import BackoffPolicy
from .scheduler.sites import hotdeal_boards

ALLOW_NETWORK_ENV = "COLLECTOR_ALLOW_NETWORK"

# docs/31 위임 수치(미승인 잠정) — docs/91 Q-37.
BACKOFF = BackoffPolicy(base=timedelta(seconds=60), factor=2, cap=timedelta(minutes=30))

# docs/31 위임 수치(미승인 잠정) — docs/91 Q-40. 실 수집 데이터로 재조정한다.
DRIFT = DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=3)

# 사이클 간 대기. 실제 폴링 주기는 사이트별 next_attempt_at이 강제하므로(하한 60s)
# 이 값은 "얼마나 촘촘히 due를 확인할까"일 뿐이다.
TICK_SECONDS = 15

# 연속 적재 실패 한계. 한 번의 DB 재시작은 견디되, 계속 실패하면 **조용히 돌지 않고 죽는다** —
# 수집은 되는데 아무것도 저장되지 않는 상태가 가장 나쁘다(compose `restart: on-failure`가 받는다).
SINK_FAILURE_LIMIT = 3


def _never() -> bool:
    """기본 종료 신호 없음. 신호 등록 같은 부작용은 프로세스 가장자리(`__main__`)에만 둔다."""
    return False


def main(
    *,
    opener=None,
    sink=None,
    sleep=time.sleep,
    clock=lambda: datetime.now(timezone.utc),
    should_stop=_never,
    max_cycles: int | None = None,
) -> int:
    now = clock()
    if os.environ.get(ALLOW_NETWORK_ENV) != "1":
        _log("refused", now, reason="network_opt_in_missing", env=ALLOW_NETWORK_ENV,
             message=f"실 네트워크 폴링은 기본 비활성입니다(정지조건). {ALLOW_NETWORK_ENV}=1 로 켜세요.")
        return 0

    fetch = _build_fetcher(opener or urllib_opener)
    sink = sink or _build_sink()
    specs = hotdeal_boards()
    states: dict = {}
    drift = DriftHistory()
    cycles = 0
    sink_failures = 0

    _log("started", now, boards=[s.name for s in specs], sink="postgres" if sink else None,
         message=None if sink else "DB 미설정(DB_HOST 없음): 적재하지 않고 로그만 남깁니다.")

    while max_cycles is None or cycles < max_cycles:
        now = clock()
        result = run_cycle(specs, states, now, fetch, BACKOFF)
        states = result.states

        written = None
        if sink is not None and result.deals:
            records = to_raw_records(result.deals, now)
            try:
                written = sink.upsert_all(records)
                sink_failures = 0
            except Exception as failure:
                # DB 일시장애가 수집 루프를 죽이면 안 된다(REL-02의 정신). 다만 **뭉개지도 않는다** —
                # 몇 건을 잃었는지 남기고, 계속 실패하면 아래에서 프로세스를 내린다.
                sink_failures += 1
                _log("sink_error", now, error=f"{type(failure).__name__}: {failure}",
                     dropped=len(records), consecutive=sink_failures)

        # written은 실패 시 부재다. "0건 적재"와 "적재 못 함"은 다른 사건이다.
        _log("cycle", now, written=written, **counters(result))

        for alert in result.alerts:
            _log("alert", alert.at, kind="blocked", site=alert.site, reason=alert.reason)

        # REL-06: 파서가 조용히 0건을 내는 구조 변경을 잡는다.
        for observation in result.observations:
            drift, drift_alerts = observe(drift, observation, DRIFT, now)
            for alert in drift_alerts:
                _log("alert", alert.at, kind="drift", site=alert.site, reason=alert.reason)

        cycles += 1

        if sink_failures >= SINK_FAILURE_LIMIT:
            _log("giving_up", now, consecutive_sink_failures=sink_failures, cycles=cycles,
                 message="적재가 연속 실패했습니다. 수집만 계속하면 표본이 늘지 않으므로 종료합니다.")
            return 1

        if should_stop():
            # SIGTERM은 사이클 중간에 프로세스를 찢지 않는다 — 여기까지 오면 이번 사이클은 온전하다.
            _log("stopped", now, cycles=cycles, reason="signal")
            return 0

        if max_cycles is None or cycles < max_cycles:
            sleep(TICK_SECONDS)
    return 0


def _log(name: str, at: datetime, **fields) -> None:
    """구조화 로그를 stdout으로. `docker logs`가 유일한 관측 창구다(OBS-01)."""
    print(event(name, at, **fields))


def _build_fetcher(opener) -> HttpFetcher:
    return HttpFetcher(opener=opener, robots=RobotsGate(opener=opener))


def _build_sink() -> RawDealSink | None:
    connection = connect_from_env()
    return RawDealSink(connection) if connection else None


class SignalStopper:
    """`docker stop`(SIGTERM)·Ctrl-C(SIGINT)를 받으면 현재 사이클을 마치고 종료하게 한다.

    틱 대기는 `time.sleep`이 아니라 `Event.wait`다. `time.sleep`은 신호 처리 후 **남은 시간을
    마저 잔다**(PEP 475). 15초를 마저 자는 사이 docker의 기본 유예(10초)가 끝나 SIGKILL이 온다.
    """

    def __init__(self) -> None:
        self._stop = threading.Event()
        signal.signal(signal.SIGTERM, self._request_stop)
        signal.signal(signal.SIGINT, self._request_stop)

    def _request_stop(self, _signum, _frame) -> None:
        self._stop.set()

    def __call__(self) -> bool:
        return self._stop.is_set()

    def sleep(self, seconds: float) -> None:
        self._stop.wait(seconds)


if __name__ == "__main__":
    stopper = SignalStopper()
    raise SystemExit(main(sleep=stopper.sleep, should_stop=stopper))
