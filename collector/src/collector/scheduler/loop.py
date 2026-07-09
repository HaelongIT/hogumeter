"""폴링 사이클 — 사이트별 격리 1회 통과. sleep 없음(주기 판정은 now 비교로).

fetch는 주입 포트다. 실 HTTP 구현체는 여기 없다 — 실 네트워크는 정지조건(CLAUDE.md).
반복 실행(while + sleep)은 이 함수를 감싸는 엔트리포인트의 몫이며, 그때 fetch 구현이 함께 온다.
"""

from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
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
    parse: Callable[[str], list[ParsedDeal]]


@dataclass(frozen=True)
class CycleResult:
    states: dict[str, SiteState]
    deals: list[ParsedDeal]
    alerts: list[Alert]


def run_cycle(
    specs: Sequence[SiteSpec],
    states: Mapping[str, SiteState],
    now: datetime,
    fetch: Callable[[SiteSpec], FetchResult],
    policy: BackoffPolicy,
) -> CycleResult:
    """due한 사이트만 1회씩 폴링·파싱하고 새 상태를 반환. 한 사이트의 실패는 다른 사이트를 막지 않는다."""
    next_states: dict[str, SiteState] = {}
    deals: list[ParsedDeal] = []
    alerts: list[Alert] = []

    for spec in specs:
        state = states.get(spec.name, SiteState(site=spec.name))
        if not is_due(state, now):
            next_states[spec.name] = state
            continue

        outcome, site_deals, status_code = _poll(spec, fetch)
        deals.extend(site_deals)
        if outcome is Outcome.BLOCKED:
            alerts.append(
                Alert(
                    site=spec.name,
                    reason=f"차단 신호 감지(status={status_code}) — 자동 중지, 재시도하지 않음",
                    at=now,
                )
            )
        next_states[spec.name] = advance(
            state, outcome, now, effective_interval(spec.interval, spec.kind), policy
        )

    return CycleResult(states=next_states, deals=deals, alerts=alerts)


def _poll(
    spec: SiteSpec, fetch: Callable[[SiteSpec], FetchResult]
) -> tuple[Outcome, list[ParsedDeal], int | None]:
    """한 사이트 폴링. fetch·parse의 어떤 예외도 TRANSIENT로 흡수한다(REL-02 격리).

    파싱 실패를 TRANSIENT로 두는 건 사이트 구조 변경이 일시 장애와 구분되지 않기 때문이다.
    구조 변경의 본격 감지는 성공률 임계(REL-06) — 이번 범위 밖(docs/91 Q-40).
    """
    try:
        result = fetch(spec)
        outcome = classify_status(result.status_code)
        if outcome is not Outcome.OK:
            return outcome, [], result.status_code
        return Outcome.OK, spec.parse(result.body), result.status_code
    except Exception:
        return Outcome.TRANSIENT, [], None
