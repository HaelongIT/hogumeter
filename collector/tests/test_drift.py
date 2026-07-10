"""REL-06 파싱 드리프트 감지 — 순수. 이동창 관측만 보고 판정한다(IO·now 주입).

왜 필요한가: 뽐뿌 셀렉터 체인이 끊겼을 때 파서는 **예외 없이 0건**을 반환했다. 실패가 아니라
성공으로 보였고, 아무도 몰랐다. "성공했는데 조용히 0건"이 사이트 구조 변경의 전형적 징후다.
"""

from datetime import datetime, timezone

import pytest

from collector.scheduler.drift import DriftHistory, DriftPolicy, observe
from collector.scheduler.loop import SiteObservation
from collector.scheduler.policy import Outcome

NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)
POLICY = DriftPolicy(window=5, min_success_rate=0.6, zero_yield_streak=3)


def _ok(deals: int = 10) -> SiteObservation:
    return SiteObservation("ppomppu", Outcome.OK, deals, deals)


def _fail() -> SiteObservation:
    return SiteObservation("ppomppu", Outcome.TRANSIENT, 0, 0)


def _feed(observations, policy=POLICY):
    history, alerts = DriftHistory(), []
    for observation in observations:
        history, emitted = observe(history, observation, policy, NOW)
        alerts.extend(emitted)
    return history, alerts


# ── 조용한 0건: 구조 변경의 전형 ────────────────────────────────────────


def test_silent_zero_yield_streak_raises_an_alert():
    """예외는 없는데 계속 0건. 파서가 사이트 변경을 못 따라간 것이다."""
    _, alerts = _feed([_ok(10), _ok(0), _ok(0), _ok(0)])

    assert len(alerts) == 1
    assert alerts[0].site == "ppomppu"
    assert "0건" in alerts[0].reason


def test_zero_yield_below_the_streak_is_silent():
    """빈 목록이 한두 번은 정상이다(새벽에 새 글이 없을 수 있다)."""
    _, alerts = _feed([_ok(10), _ok(0), _ok(0)])

    assert alerts == []


def test_a_successful_yield_resets_the_zero_streak():
    _, alerts = _feed([_ok(0), _ok(0), _ok(5), _ok(0), _ok(0)])

    assert alerts == []


def test_never_yielding_site_does_not_alert_before_the_streak():
    """처음부터 0건인 사이트도 임계까지는 기다린다(기동 직후 오탐 방지)."""
    _, alerts = _feed([_ok(0), _ok(0)])

    assert alerts == []


# ── 성공률 저하 ─────────────────────────────────────────────────────────


def test_success_rate_below_threshold_raises_an_alert():
    """창 5회 중 3회 실패 → 성공률 0.4 < 0.6."""
    _, alerts = _feed([_ok(), _fail(), _fail(), _ok(), _fail()])

    assert len(alerts) == 1
    assert "성공률" in alerts[0].reason


def test_partial_window_is_not_judged():
    """관측이 창을 채우기 전엔 판정하지 않는다 — 첫 실패로 알림이 터지면 안 된다."""
    _, alerts = _feed([_fail(), _fail()])

    assert alerts == []


def test_healthy_site_never_alerts():
    _, alerts = _feed([_ok() for _ in range(10)])

    assert alerts == []


# ── 알림 반복 억제 ──────────────────────────────────────────────────────


def test_alert_is_not_repeated_every_cycle():
    """같은 증상으로 매 사이클 알림이 오면 아무도 안 본다."""
    _, alerts = _feed([_ok(10)] + [_ok(0)] * 6)

    assert len(alerts) == 1


def test_recovery_re_arms_the_alert():
    """회복 후 다시 나빠지면 다시 알린다."""
    _, alerts = _feed([_ok(10)] + [_ok(0)] * 4 + [_ok(7)] + [_ok(0)] * 3)

    assert len(alerts) == 2


# ── 사이트 격리 ─────────────────────────────────────────────────────────


def test_sites_are_tracked_independently():
    history, alerts = DriftHistory(), []
    for _ in range(4):
        history, emitted = observe(history, SiteObservation("ppomppu", Outcome.OK, 0, 0), POLICY, NOW)
        alerts.extend(emitted)
        history, emitted = observe(history, SiteObservation("ruliweb", Outcome.OK, 9, 9), POLICY, NOW)
        alerts.extend(emitted)

    assert [a.site for a in alerts] == ["ppomppu"]


# ── BLOCKED는 드리프트가 아니다 ─────────────────────────────────────────


def test_blocked_is_not_counted_as_drift():
    """차단은 이미 사이트 중지 + Alert로 처리된다. 드리프트 알림까지 겹치면 소음이다."""
    blocked = SiteObservation("ppomppu", Outcome.BLOCKED, 0, 0)

    _, alerts = _feed([_ok()] + [blocked] * 5)

    assert alerts == []


def test_policy_rejects_nonsense():
    with pytest.raises(ValueError):
        DriftPolicy(window=0, min_success_rate=0.6, zero_yield_streak=3)
    with pytest.raises(ValueError):
        DriftPolicy(window=5, min_success_rate=1.5, zero_yield_streak=3)


# ── 딜은 나오는데 **가격이 하나도 없다** ────────────────────────────────
#
# REL-06은 `deals == 0` 연속만 본다. 그런데 오늘 찾은 파서 결함 다섯 중 셋은
# **딜은 그대로 나오는데 값이 틀리거나 사라지는** 부류였다(품절 표식 사망·가격 오검출·배송비 유실).
#
# 그중 기계가 잡을 수 있는 것은 하나다: 제목 셀렉터가 끊기면 딜 수는 그대로인데
# **가격이 전부 None**이 된다. 루리웹은 정상 상태에서도 36%가 가격 없음이라 "일부"로는 못 잡는다 —
# **전부**일 때만 잡는다.


def test_deals_without_any_price_raise_drift():
    policy = DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=3)
    history = DriftHistory()

    for _ in range(3):
        history, alerts = observe(history, _obs("ruliweb", deals=28, priced=0), policy, NOW)

    assert alerts
    assert "가격이 하나도 없습니다" in alerts[0].reason
    assert "**" not in alerts[0].reason  # 콘솔 로그는 렌더링되지 않는다


def test_some_deals_without_price_is_normal():
    """루리웹은 정상 상태에서도 28건 중 10건이 가격 없음이다. 오차단은 게이트를 꺼지게 만든다."""
    policy = DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=3)
    history = DriftHistory()

    for _ in range(6):
        history, alerts = observe(history, _obs("ruliweb", deals=28, priced=18), policy, NOW)
        assert alerts == []


def test_price_yield_recovers_and_can_alert_again():
    policy = DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=2)
    history = DriftHistory()

    for _ in range(2):
        history, first = observe(history, _obs("ppomppu", deals=21, priced=0), policy, NOW)
    assert first
    history, _ = observe(history, _obs("ppomppu", deals=21, priced=21), policy, NOW)  # 회복
    for _ in range(2):
        history, again = observe(history, _obs("ppomppu", deals=21, priced=0), policy, NOW)

    assert again  # 무장 해제 후 다시 알린다


def test_zero_deals_is_still_the_old_signal():
    """`deals == 0`은 여전히 별개 신호다. 두 사실을 한 알림으로 뭉치지 않는다."""
    policy = DriftPolicy(window=10, min_success_rate=0.6, zero_yield_streak=2)
    history = DriftHistory()

    for _ in range(2):
        history, alerts = observe(history, _obs("fmkorea", deals=0, priced=0), policy, NOW)

    assert alerts and "0건" in alerts[0].reason


def _obs(site: str, *, deals: int, priced: int) -> SiteObservation:
    return SiteObservation(site=site, outcome=Outcome.OK, deal_count=deals, priced_count=priced)
