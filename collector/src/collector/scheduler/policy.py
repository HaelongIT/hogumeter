"""폴링 정책 — 순수 함수/값만(네트워크·시계 없음). now는 항상 주입받는다.

핵심 분기는 결과 3분해다. 흔한 구현은 "모든 실패에 지수 백오프"지만 SEC-08은 차단 신호에
**재시도 강행을 금지**한다 — 그래서 일시 장애(TRANSIENT)만 백오프하고, 차단(BLOCKED)은
해당 사이트를 즉시 중지한다(다른 사이트는 계속 돈다 — REL-02).

레이트 리밋 하한(_FLOOR)은 "설정으로 완화 불가"(SEC-08)라 상수로 못박는다. 설정값이 하한보다
짧으면 하한으로 끌어올린다 — 반대 방향(더 느리게)은 허용.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timedelta
from enum import Enum


class SiteKind(Enum):
    """레이트 하한이 갈리는 축(PERF-05)."""

    BOARD = "BOARD"  # 핫딜 게시판 — 1req/min
    MARKETPLACE = "MARKETPLACE"  # 중고 검색 — 10분


_FLOOR: dict[SiteKind, timedelta] = {
    SiteKind.BOARD: timedelta(seconds=60),
    SiteKind.MARKETPLACE: timedelta(minutes=10),
}


def effective_interval(configured: timedelta, kind: SiteKind) -> timedelta:
    """설정 주기를 사이트 종류의 하한으로 clamp. 하한보다 짧게는 절대 못 간다."""
    return max(configured, _FLOOR[kind])


class Outcome(Enum):
    OK = "OK"
    TRANSIENT = "TRANSIENT"  # 5xx·타임아웃·파싱 실패 → 지수 백오프 후 재시도
    BLOCKED = "BLOCKED"  # 403·429 → 자동 중지 + 관리 알림, 재시도 금지


_BLOCK_SIGNALS = frozenset({403, 429})


def classify_status(status_code: int) -> Outcome:
    """HTTP 상태 → 결과. 차단 신호는 재시도 대상이 아니다(SEC-08)."""
    if status_code in _BLOCK_SIGNALS:
        return Outcome.BLOCKED
    if 200 <= status_code < 300:
        return Outcome.OK
    return Outcome.TRANSIENT


@dataclass(frozen=True)
class BackoffPolicy:
    """지수 백오프 수치. **미승인 잠정값이라 주입받는다**(docs/91 Q-37, Q-14 선례).

    지터 없음 — 테스트 결정성(docs/21 "확률·랜덤 없음"). 1인용·사이트당 1커넥션이라
    thundering herd 대상이 아니다.
    """

    base: timedelta
    factor: int
    cap: timedelta


def backoff_delay(failures: int, policy: BackoffPolicy) -> timedelta:
    """연속 실패 n회 → 다음 시도까지의 지연. cap에서 포화.

    초(float)로 계산해 cap과 먼저 비교한다 — `timedelta * 2**98`은 C int 오버플로를 낸다.
    float 지수는 넘치면 inf가 되므로 비교가 그대로 성립한다.
    """
    if failures < 1:
        raise ValueError(f"failures는 1 이상이어야 한다: {failures}")
    delay_seconds = policy.base.total_seconds() * float(policy.factor) ** (failures - 1)
    if delay_seconds >= policy.cap.total_seconds():
        return policy.cap
    return timedelta(seconds=delay_seconds)


@dataclass(frozen=True)
class SiteState:
    """사이트별 폴링 커서. 이 클래스 자체는 순수 값이고, 영속화는 `db/site_poll_state_sink.py`가

    한다(REL-03 Q-59) — 기동 시 그 값을 읽어 seed하고, 매 사이클 다시 써 넣는다. 차단당한 사이트의
    재개는 운영자가 그 영속 행을 직접 UPDATE하는 것(`decisions-needed` D-3, 2026-07-24 확정) —
    별도 명령·API 없이, 다음 재시작이 그 값을 그대로 읽어 반영한다.

    next_attempt_at=None은 두 가지 뜻이라 단독으로 읽지 말고 is_due()를 쓸 것:
    신규 상태(즉시 due) 또는 stopped(영구 미due). stopped가 우선한다.
    """

    site: str
    last_successful_poll: datetime | None = None
    consecutive_failures: int = 0
    next_attempt_at: datetime | None = None
    stopped: bool = False


def is_due(state: SiteState, now: datetime) -> bool:
    if state.stopped:
        return False
    return state.next_attempt_at is None or state.next_attempt_at <= now


def advance(
    state: SiteState,
    outcome: Outcome,
    now: datetime,
    interval: timedelta,
    policy: BackoffPolicy,
) -> SiteState:
    """폴링 1회 결과를 상태에 반영. stopped는 이 함수 안에서는 종착 상태 — 재개는 영속 행을

    운영자가 직접 고쳐 다음 재시작에 반영하는 것이다(decisions-needed D-3)."""
    if state.stopped:
        return state
    if outcome is Outcome.OK:
        return replace(
            state,
            last_successful_poll=now,
            consecutive_failures=0,
            next_attempt_at=now + interval,
        )
    if outcome is Outcome.TRANSIENT:
        failures = state.consecutive_failures + 1
        return replace(
            state,
            consecutive_failures=failures,
            next_attempt_at=now + backoff_delay(failures, policy),
        )
    return replace(state, stopped=True, next_attempt_at=None)


@dataclass(frozen=True)
class Alert:
    """관리 알림 값(SEC-08·REL-06). 발송은 미래의 포트 — 텔레그램은 docs/91 Q-20 블록."""

    site: str
    reason: str
    at: datetime
