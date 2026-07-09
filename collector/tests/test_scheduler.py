"""폴링 스케줄러 — 순수 정책(레이트 하한·백오프·결과 분류·상태 전이) + 사이클 루프.

실 네트워크 없음: fetch는 주입 포트, 테스트는 fake fetcher. sleep·랜덤 없음(docs/21).
"""

from datetime import datetime, timedelta, timezone

import pytest

from collector.parsers.models import ParsedDeal
from collector.scheduler.loop import CycleResult, FetchResult, SiteSpec, run_cycle
from collector.scheduler.policy import (
    Alert,
    BackoffPolicy,
    Outcome,
    SiteKind,
    SiteState,
    advance,
    backoff_delay,
    classify_status,
    effective_interval,
    is_due,
)

NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)
BACKOFF = BackoffPolicy(base=timedelta(seconds=60), factor=2, cap=timedelta(minutes=30))


# ── 레이트 리밋 하한 (SEC-08: 설정으로 완화 불가) ────────────────────────


def test_board_interval_is_clamped_up_to_floor():
    assert effective_interval(timedelta(seconds=30), SiteKind.BOARD) == timedelta(seconds=60)


def test_board_interval_above_floor_is_honored():
    assert effective_interval(timedelta(seconds=120), SiteKind.BOARD) == timedelta(seconds=120)


def test_marketplace_floor_is_ten_minutes():
    assert effective_interval(timedelta(seconds=60), SiteKind.MARKETPLACE) == timedelta(minutes=10)


# ── 결과 분류 (403/429는 백오프가 아니라 중지) ──────────────────────────


@pytest.mark.parametrize(
    "status,expected",
    [
        (200, Outcome.OK),
        (403, Outcome.BLOCKED),
        (429, Outcome.BLOCKED),
        (500, Outcome.TRANSIENT),
        (502, Outcome.TRANSIENT),
        (503, Outcome.TRANSIENT),
    ],
)
def test_classify_status(status, expected):
    assert classify_status(status) is expected


# ── 지수 백오프 (결정적, 지터 없음) ─────────────────────────────────────


@pytest.mark.parametrize(
    "failures,expected_seconds",
    [(1, 60), (2, 120), (3, 240), (4, 480)],
)
def test_backoff_grows_exponentially(failures, expected_seconds):
    assert backoff_delay(failures, BACKOFF) == timedelta(seconds=expected_seconds)


def test_backoff_saturates_at_cap():
    assert backoff_delay(99, BACKOFF) == timedelta(minutes=30)


def test_backoff_rejects_non_positive_failures():
    with pytest.raises(ValueError):
        backoff_delay(0, BACKOFF)


# ── 상태 전이 ───────────────────────────────────────────────────────────


def _fresh(site: str = "ruliweb") -> SiteState:
    return SiteState(site=site)


def test_ok_resets_failures_and_records_successful_poll():
    state = advance(_fresh(), Outcome.OK, NOW, timedelta(seconds=60), BACKOFF)

    assert state.consecutive_failures == 0
    assert state.last_successful_poll == NOW
    assert state.next_attempt_at == NOW + timedelta(seconds=60)
    assert state.stopped is False


def test_transient_increments_failures_and_backs_off():
    once = advance(_fresh(), Outcome.TRANSIENT, NOW, timedelta(seconds=60), BACKOFF)
    twice = advance(once, Outcome.TRANSIENT, NOW, timedelta(seconds=60), BACKOFF)

    assert once.consecutive_failures == 1
    assert once.next_attempt_at == NOW + timedelta(seconds=60)
    assert once.stopped is False

    assert twice.consecutive_failures == 2
    assert twice.next_attempt_at == NOW + timedelta(seconds=120)
    assert twice.last_successful_poll is None


def test_blocked_stops_the_site_without_retry():
    state = advance(_fresh(), Outcome.BLOCKED, NOW, timedelta(seconds=60), BACKOFF)

    assert state.stopped is True
    assert state.next_attempt_at is None  # 재시도 강행 금지(SEC-08)
    assert is_due(state, NOW + timedelta(days=365)) is False


def test_stopped_state_is_terminal():
    stopped = advance(_fresh(), Outcome.BLOCKED, NOW, timedelta(seconds=60), BACKOFF)

    assert advance(stopped, Outcome.OK, NOW, timedelta(seconds=60), BACKOFF) == stopped


def test_fresh_state_is_due_immediately():
    assert is_due(_fresh(), NOW) is True


# ── 사이클 루프 ─────────────────────────────────────────────────────────


class FakeFetcher:
    """호출 기록을 남기는 fake. 사이트명 → FetchResult 또는 raise할 예외."""

    def __init__(self, responses: dict):
        self._responses = responses
        self.calls: list[str] = []

    def __call__(self, spec: SiteSpec) -> FetchResult:
        self.calls.append(spec.name)
        response = self._responses[spec.name]
        if isinstance(response, Exception):
            raise response
        return response


def _deal(site: str) -> ParsedDeal:
    return ParsedDeal(site=site, post_id="1", title="딜", url="u")


def _spec(name: str = "ruliweb", kind: SiteKind = SiteKind.BOARD) -> SiteSpec:
    return SiteSpec(
        name=name,
        kind=kind,
        interval=timedelta(seconds=60),
        url=f"https://example.test/{name}",
        encoding="utf-8",
        parse=lambda body, now: [_deal(name)],
    )


def test_due_site_is_fetched_parsed_and_advanced():
    spec = _spec()
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=200, body="<html/>")})

    result = run_cycle([spec], {}, NOW, fetch, BACKOFF)

    assert isinstance(result, CycleResult)
    assert fetch.calls == ["ruliweb"]
    assert [d.site for d in result.deals] == ["ruliweb"]
    assert result.alerts == []
    assert result.states["ruliweb"].last_successful_poll == NOW


def test_site_not_yet_due_is_not_fetched():
    spec = _spec()
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=200, body="<html/>")})
    not_due = SiteState(site="ruliweb", next_attempt_at=NOW + timedelta(seconds=30))

    result = run_cycle([spec], {"ruliweb": not_due}, NOW, fetch, BACKOFF)

    assert fetch.calls == []
    assert result.deals == []
    assert result.states["ruliweb"] == not_due


def test_one_site_failure_does_not_block_the_others():
    specs = [_spec("ruliweb"), _spec("fmkorea")]
    fetch = FakeFetcher(
        {
            "ruliweb": RuntimeError("연결 실패"),
            "fmkorea": FetchResult(status_code=200, body="<html/>"),
        }
    )

    result = run_cycle(specs, {}, NOW, fetch, BACKOFF)

    assert fetch.calls == ["ruliweb", "fmkorea"]  # 격리(REL-02)
    assert [d.site for d in result.deals] == ["fmkorea"]
    assert result.states["ruliweb"].consecutive_failures == 1
    assert result.states["ruliweb"].stopped is False
    assert result.states["fmkorea"].last_successful_poll == NOW


def test_parser_failure_is_isolated_as_transient():
    def exploding_parse(body, now):
        raise ValueError("셀렉터 불일치")

    spec = SiteSpec(
        name="ruliweb",
        kind=SiteKind.BOARD,
        interval=timedelta(seconds=60),
        url="https://example.test/ruliweb",
        encoding="utf-8",
        parse=exploding_parse,
    )
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=200, body="<html/>")})

    result = run_cycle([spec], {}, NOW, fetch, BACKOFF)

    assert result.deals == []
    assert result.states["ruliweb"].consecutive_failures == 1
    assert result.states["ruliweb"].last_successful_poll is None


def test_block_signal_stops_site_and_emits_alert():
    spec = _spec()
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=403, body="")})

    result = run_cycle([spec], {}, NOW, fetch, BACKOFF)

    assert result.states["ruliweb"].stopped is True
    assert result.deals == []
    assert len(result.alerts) == 1
    alert = result.alerts[0]
    assert isinstance(alert, Alert)
    assert alert.site == "ruliweb"
    assert alert.at == NOW
    assert "403" in alert.reason


def test_stopped_site_is_never_fetched_again():
    spec = _spec()
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=403, body="")})

    first = run_cycle([spec], {}, NOW, fetch, BACKOFF)
    later = NOW + timedelta(days=1)
    second = run_cycle([spec], first.states, later, fetch, BACKOFF)

    assert fetch.calls == ["ruliweb"]  # 두 번째 사이클에선 미호출
    assert second.alerts == []  # 알림도 1회뿐
    assert second.states["ruliweb"].stopped is True


def test_configured_interval_below_floor_is_clamped_in_cycle():
    spec = SiteSpec(
        name="ruliweb",
        kind=SiteKind.BOARD,
        interval=timedelta(seconds=1),  # 하한 위반 시도
        url="https://example.test/ruliweb",
        encoding="utf-8",
        parse=lambda body, now: [],
    )
    fetch = FakeFetcher({"ruliweb": FetchResult(status_code=200, body="")})

    result = run_cycle([spec], {}, NOW, fetch, BACKOFF)

    assert result.states["ruliweb"].next_attempt_at == NOW + timedelta(seconds=60)
