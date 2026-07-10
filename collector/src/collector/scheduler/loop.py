"""폴링 사이클 — 사이트별 격리 1회 통과. sleep 없음(주기 판정은 now 비교로).

fetch는 주입 포트다. 실 HTTP 구현체는 여기 없다 — 실 네트워크는 정지조건(CLAUDE.md).
반복 실행(while + sleep)은 이 함수를 감싸는 엔트리포인트의 몫이며, 그때 fetch 구현이 함께 온다.
"""

from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass, field
from datetime import datetime, timedelta

from ..parsers.models import ParsedDeal
from .policy import (
    Alert,
    BackoffPolicy,
    Outcome,
    SiteKind,
    SiteState,
    advance,
    classify_status,
    effective_interval,
    is_due,
)


@dataclass(frozen=True)
class FetchResult:
    status_code: int
    body: str


@dataclass(frozen=True)
class SiteSpec:
    """폴링 대상 1개. parse는 순수 파서(html/json → DTO).

    url·encoding에 기본값을 주지 않는다 — "URL 없는 스펙"이 조용히 만들어지면 fetcher가 빈 주소를 친다.
    encoding은 사이트마다 다르다(뽐뿌는 cp949, docs/98).
    """

    name: str
    kind: SiteKind
    interval: timedelta
    url: str
    encoding: str
    # 목록 시각이 "당일 21:10" 형태라 해석에 폴링 시각이 필요하다(pipeline/timestamps).
    parse: Callable[[str, datetime], list[ParsedDeal]]


@dataclass(frozen=True)
class SiteObservation:
    """이번 사이클에 이 사이트가 어떻게 됐나. REL-06 드리프트 판정의 입력.

    `ok=True, deal_count=0`이 구조 변경의 전형적 징후다 — 예외 없이 조용히 빈 목록을 낸다.
    (실제로 뽐뿌 셀렉터 체인이 끊겼을 때 그랬다.)
    """

    site: str
    outcome: Outcome
    deal_count: int


@dataclass(frozen=True)
class CycleResult:
    states: dict[str, SiteState]
    deals: list[ParsedDeal]
    alerts: list[Alert]
    # due가 아니어서 건너뛴 사이트는 관측이 없다(폴링하지 않았으므로 판단할 근거도 없다).
    observations: list[SiteObservation] = field(default_factory=list)


def run_cycle(
    specs: Sequence[SiteSpec],
    states: Mapping[str, SiteState],
    now: datetime,
    fetch: Callable[[SiteSpec], FetchResult],
    policy: BackoffPolicy,
    interval_for: Callable[[SiteSpec], timedelta] | None = None,
) -> CycleResult:
    """due한 사이트만 1회씩 폴링·파싱하고 새 상태를 반환. 한 사이트의 실패는 다른 사이트를 막지 않는다.

    `interval_for`는 **다음 시도까지의 주기를 정하는 포트**다. 기본은 사이트 종류의 레이트 하한
    (게시판 60초 / 마켓 600초)만 적용한다. `__main__`은 여기에 **robots의 `Crawl-delay`**를 얹는다
    (SEC-08: 선언된 지연이 우리 주기보다 길면 그쪽을 따른다). 포트로 뺀 이유는 robots 조회가
    네트워크를 타기 때문이다 — 순수 루프가 소켓을 알 필요는 없다.
    """
    next_states: dict[str, SiteState] = {}
    deals: list[ParsedDeal] = []
    alerts: list[Alert] = []
    observations: list[SiteObservation] = []

    for spec in specs:
        state = states.get(spec.name, SiteState(site=spec.name))
        if not is_due(state, now):
            next_states[spec.name] = state
            continue

        outcome, site_deals, status_code = _poll(spec, fetch, now)
        deals.extend(site_deals)
        observations.append(SiteObservation(spec.name, outcome, len(site_deals)))
        if outcome is Outcome.BLOCKED:
            alerts.append(
                Alert(
                    site=spec.name,
                    # 이 문자열은 콘솔로 출력된다. cp949(Windows)에 없는 문자를 넣지 말 것.
                    reason=f"차단 신호 감지(status={status_code}): 자동 중지, 재시도하지 않음",
                    at=now,
                )
            )
        interval = (
            interval_for(spec) if interval_for else effective_interval(spec.interval, spec.kind)
        )
        next_states[spec.name] = advance(state, outcome, now, interval, policy)

    return CycleResult(states=next_states, deals=deals, alerts=alerts, observations=observations)


def _poll(
    spec: SiteSpec, fetch: Callable[[SiteSpec], FetchResult], now: datetime
) -> tuple[Outcome, list[ParsedDeal], int | None]:
    """한 사이트 폴링. fetch·parse의 어떤 예외도 TRANSIENT로 흡수한다(REL-02 격리).

    파싱 실패를 TRANSIENT로 두는 건 사이트 구조 변경이 일시 장애와 구분되지 않기 때문이다.
    구조 변경의 본격 감지는 성공률 임계(REL-06) — `scheduler/drift.py`가 맡는다.
    이 함수는 관측(`SiteObservation`)만 내고 판정하지 않는다.
    """
    try:
        result = fetch(spec)
        outcome = classify_status(result.status_code)
        if outcome is not Outcome.OK:
            return outcome, [], result.status_code
        return Outcome.OK, spec.parse(result.body, now), result.status_code
    except Exception:
        return Outcome.TRANSIENT, [], None
