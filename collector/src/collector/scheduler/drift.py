"""REL-06 파싱 드리프트 감지 — 순수. 이동창 관측만 보고 판정한다(now는 주입).

사이트 구조가 바뀌면 파서는 **터지지 않는다.** 셀렉터가 아무것도 못 찾아 조용히 빈 목록을 낸다.
실제로 뽐뿌에서 그랬다 — 오픈소스 셀렉터 체인이 첫 단계에서 끊겼는데 예외 없이 0건이었고,
그 사실이 문서에 "셀렉터 전무"로 잘못 기록된 채 석 달을 갔다.

그래서 두 가지를 본다:
  1. **조용한 0건** — 성공(OK)인데 연속으로 딜이 0건. 구조 변경의 전형.
  2. **성공률 저하** — 창 안에서 실패(TRANSIENT) 비율이 임계를 넘음.

BLOCKED는 세지 않는다. 차단은 이미 사이트 중지 + Alert로 처리되며, 드리프트 알림까지 겹치면 소음이다.

임계값은 **미승인 잠정**이라 주입받는다(docs/31 위임 수치, `docs/91` Q-45 — 실 수집 후 재조정). 알림 문구는 콘솔로
출력되므로 cp949에 없는 문자(em dash·이모지)를 쓰지 않는다.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import datetime

from .loop import SiteObservation
from .policy import Alert, Outcome


@dataclass(frozen=True)
class DriftPolicy:
    """미승인 잠정 수치 — 주입받는다(Q-14·Q-37 선례)."""

    window: int
    min_success_rate: float
    zero_yield_streak: int

    def __post_init__(self) -> None:
        if self.window < 1:
            raise ValueError(f"window는 1 이상이어야 한다: {self.window}")
        if not 0.0 <= self.min_success_rate <= 1.0:
            raise ValueError(f"min_success_rate는 0~1이어야 한다: {self.min_success_rate}")
        if self.zero_yield_streak < 1:
            raise ValueError(f"zero_yield_streak는 1 이상이어야 한다: {self.zero_yield_streak}")


@dataclass(frozen=True)
class DriftHistory:
    """사이트별 이동창 + 이미 알린 사이트. 불변 — `observe`가 새 값을 돌려준다."""

    windows: dict[str, tuple[SiteObservation, ...]] = field(default_factory=dict)
    alerted: frozenset[str] = frozenset()


def observe(
    history: DriftHistory, observation: SiteObservation, policy: DriftPolicy, now: datetime
) -> tuple[DriftHistory, list[Alert]]:
    """관측 1건을 반영하고 새 이력 + 이번에 새로 발생한 알림을 돌려준다."""
    if observation.outcome is Outcome.BLOCKED:
        return history, []  # 차단은 드리프트가 아니다(이미 중지 + Alert)

    site = observation.site
    window = (*history.windows.get(site, ()), observation)[-policy.window :]
    alerted = history.alerted

    # 회복하면 다시 알릴 수 있게 무장 해제한다.
    if _healthy(observation):
        alerted = alerted - {site}

    reason = _diagnose(window, policy)
    alerts: list[Alert] = []
    if reason and site not in alerted:
        alerts.append(Alert(site=site, reason=reason, at=now))
        alerted = alerted | {site}

    return replace(history, windows={**history.windows, site: window}, alerted=alerted), alerts


def _healthy(observation: SiteObservation) -> bool:
    return observation.outcome is Outcome.OK and observation.deal_count > 0


def _diagnose(window: tuple[SiteObservation, ...], policy: DriftPolicy) -> str | None:
    streak = _zero_yield_streak(window)
    if streak >= policy.zero_yield_streak:
        return f"파싱은 성공했는데 {streak}회 연속 0건입니다. 사이트 구조 변경 의심(REL-06)."

    # 창을 채우기 전엔 판정하지 않는다 — 첫 실패로 알림이 터지면 안 된다.
    if len(window) < policy.window:
        return None

    successes = sum(1 for o in window if o.outcome is Outcome.OK)
    rate = successes / len(window)
    if rate < policy.min_success_rate:
        return (
            f"최근 {len(window)}회 수집 성공률 {rate:.0%}가 임계"
            f" {policy.min_success_rate:.0%} 아래입니다. 수집 불안정(REL-06)."
        )
    return None


def _zero_yield_streak(window: tuple[SiteObservation, ...]) -> int:
    streak = 0
    for observation in reversed(window):
        if observation.outcome is Outcome.OK and observation.deal_count == 0:
            streak += 1
        else:
            break
    return streak
