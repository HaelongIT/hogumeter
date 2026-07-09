"""컨테이너 엔트리포인트 — 레지스트리·fetcher·스케줄러를 조립한다.

**실 네트워크 폴링은 기본 비활성이다.** CLAUDE.md의 정지조건("실사이트 크롤링")을 산문이 아니라
환경변수 게이트로 강제한다 — `COLLECTOR_ALLOW_NETWORK=1` 없이는 opener를 한 번도 호출하지 않는다.

**DB 적재는 아직 없다**(docs/91 Q-36: psycopg 의존 승인 + decisions-needed D-3 선결).
그래서 opt-in해도 수집 결과는 화면 출력뿐이다 — 1차 검증용 스모크로 쓴다.
"""

from __future__ import annotations

import os
import time
from datetime import datetime, timedelta, timezone

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
    sleep=time.sleep,
    clock=lambda: datetime.now(timezone.utc),
    max_cycles: int | None = None,
) -> int:
    if os.environ.get(ALLOW_NETWORK_ENV) != "1":
        print(
            f"collector: 실 네트워크 폴링은 기본 비활성입니다(정지조건).\n"
            f"  켜려면 {ALLOW_NETWORK_ENV}=1 로 실행하세요.\n"
            f"  주의: DB 적재는 아직 구현되지 않았습니다(docs/91 Q-36). 결과는 화면 출력뿐입니다."
        )
        return 0

    fetch = _build_fetcher(opener or urllib_opener)
    specs = hotdeal_boards()
    states: dict = {}
    cycles = 0

    print(f"collector: {len(specs)}개 게시판 폴링 시작 (게시판당 1req/min 하한, robots 존중)")
    while max_cycles is None or cycles < max_cycles:
        result = run_cycle(specs, states, clock(), fetch, BACKOFF)
        states = result.states
        _report(result)
        cycles += 1
        if max_cycles is None or cycles < max_cycles:
            sleep(TICK_SECONDS)
    return 0


def _build_fetcher(opener) -> HttpFetcher:
    return HttpFetcher(opener=opener, robots=RobotsGate(opener=opener))


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
