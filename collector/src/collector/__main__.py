"""컨테이너 엔트리포인트 — 레지스트리·fetcher·스케줄러·적재기를 조립한다.

두 개의 독립된 스위치가 있다:

1. **실 네트워크 폴링**은 기본 비활성. CLAUDE.md의 정지조건("실사이트 크롤링")을 산문이 아니라
   환경변수 게이트로 강제한다 — `COLLECTOR_ALLOW_NETWORK=1` 없이는 opener를 한 번도 호출하지 않는다.
2. **DB 적재**는 `DB_HOST`가 있을 때만. 없으면 수집 결과를 화면에만 출력한다(그 사실을 숨기지 않는다).

커서(사이트별 폴링 상태)는 아직 영속화하지 않는다 — 차단당한 사이트의 재개 경로가 미결이라
(`decisions-needed` D-3) 지금 디스크에 남기면 영구 중지가 된다. 재시작하면 상태가 초기화된다.
"""

from __future__ import annotations

import os
import time
from datetime import datetime, timedelta, timezone

from .db.raw_deal_sink import RawDealSink, connect_from_env
from .pipeline.ingest import to_raw_records
from .scheduler.fetcher import HttpFetcher, RobotsGate, urllib_opener
from .scheduler.loop import CycleResult, run_cycle
from .scheduler.policy import BackoffPolicy
from .scheduler.sites import hotdeal_boards

ALLOW_NETWORK_ENV = "COLLECTOR_ALLOW_NETWORK"

# docs/31 위임 수치(미승인 잠정) — docs/91 Q-37.
BACKOFF = BackoffPolicy(base=timedelta(seconds=60), factor=2, cap=timedelta(minutes=30))

# 사이클 간 대기. 실제 폴링 주기는 사이트별 next_attempt_at이 강제하므로(하한 60s)
# 이 값은 "얼마나 촘촘히 due를 확인할까"일 뿐이다.
TICK_SECONDS = 15


def main(
    *,
    opener=None,
    sink=None,
    sleep=time.sleep,
    clock=lambda: datetime.now(timezone.utc),
    max_cycles: int | None = None,
) -> int:
    if os.environ.get(ALLOW_NETWORK_ENV) != "1":
        print(
            f"collector: 실 네트워크 폴링은 기본 비활성입니다(정지조건).\n"
            f"  켜려면 {ALLOW_NETWORK_ENV}=1 로 실행하세요."
        )
        return 0

    fetch = _build_fetcher(opener or urllib_opener)
    sink = sink or _build_sink()
    specs = hotdeal_boards()
    states: dict = {}
    cycles = 0

    print(f"collector: {len(specs)}개 게시판 폴링 시작 (게시판당 1req/min 하한, robots 존중)")
    if sink is None:
        print("  DB 미설정(DB_HOST 없음): 수집 결과를 적재하지 않고 화면에만 출력합니다.")

    while max_cycles is None or cycles < max_cycles:
        now = clock()
        result = run_cycle(specs, states, now, fetch, BACKOFF)
        states = result.states
        _report(result)
        if sink is not None and result.deals:
            written = sink.upsert_all(to_raw_records(result.deals, now))
            print(f"  적재: raw_deal_post {written}건 업서트")
        cycles += 1
        if max_cycles is None or cycles < max_cycles:
            sleep(TICK_SECONDS)
    return 0


def _build_fetcher(opener) -> HttpFetcher:
    return HttpFetcher(opener=opener, robots=RobotsGate(opener=opener))


def _build_sink() -> RawDealSink | None:
    connection = connect_from_env()
    return RawDealSink(connection) if connection else None


def _report(result: CycleResult) -> None:
    """stdout은 cp949 콘솔(Windows)에서도 인코딩돼야 한다 — em dash·이모지 금지.

    실제로 `—`가 UnicodeEncodeError로 엔트리포인트를 죽였다. capsys는 utf-8로 캡처하므로
    단위 테스트가 이를 못 잡는다(docs/99 2026-07-09).
    """
    if result.deals:
        by_site: dict[str, int] = {}
        for deal in result.deals:
            by_site[deal.site] = by_site.get(deal.site, 0) + 1
        print("  수집:", ", ".join(f"{site} {n}건" for site, n in sorted(by_site.items())))
    for alert in result.alerts:
        print(f"  [경고] {alert.site}: {alert.reason}")
    stopped = [name for name, state in result.states.items() if state.stopped]
    if stopped:
        print(f"  중지된 사이트(수동 재개 필요, decisions-needed D-3): {', '.join(stopped)}")


if __name__ == "__main__":
    raise SystemExit(main())
