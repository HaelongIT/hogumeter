"""OBS-01 구조화 로그 · OBS-02 카운터 — 순수 함수(now 주입, IO 없음).

실 폴링을 켜면 사람은 `docker logs`만 본다. 사이클마다 무엇을 몇 건 수집했고 무엇이 실패했는지가
기계로 읽히는 형태여야 한다. 그리고 **한글을 이스케이프해 cp949 콘솔에서도 죽지 않아야 한다** —
em dash 하나로 엔트리포인트가 죽은 적이 있다(docs/99).
"""

import json
from datetime import datetime, timedelta, timezone

from collector.observability import counters, event
from collector.parsers.models import ParsedDeal
from collector.scheduler.loop import CycleResult, SiteObservation
from collector.scheduler.policy import Alert, Outcome, SiteState

NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)


# ── event(): JSON 한 줄 ────────────────────────────────────────────────


def test_event_is_one_line_of_valid_json():
    line = event("cycle", NOW, deals=69)

    assert "\n" not in line
    assert json.loads(line) == {"ts": "2026-07-09T12:00:00+00:00", "event": "cycle", "deals": 69}


def test_event_escapes_non_ascii_so_the_console_never_dies():
    """Windows 콘솔은 cp949다. `ensure_ascii`로 한글을 \\uXXXX로 빼면 어떤 인코딩에서도 안전하다."""
    line = event("alert", NOW, site="ppomppu", reason="차단 신호 감지")

    line.encode("cp949")  # 실패하면 UnicodeEncodeError
    line.encode("ascii")
    assert json.loads(line)["reason"] == "차단 신호 감지"  # 값은 온전하다


def test_event_keeps_timestamp_first_and_tz_aware():
    parsed = json.loads(event("x", NOW))

    assert list(parsed)[:2] == ["ts", "event"]
    assert datetime.fromisoformat(parsed["ts"]).tzinfo is not None


def test_event_drops_none_fields():
    """`None`은 "값 없음"이지 로그에 남길 사실이 아니다."""
    assert "written" not in json.loads(event("cycle", NOW, deals=1, written=None))


# ── counters(): 사이클 카운터 (OBS-02) ─────────────────────────────────


def _result(observations, alerts=(), stopped=(), deals=()) -> CycleResult:
    states = {o.site: SiteState(site=o.site, stopped=o.site in stopped) for o in observations}
    return CycleResult(states=states, deals=list(deals), alerts=list(alerts), observations=observations)


def _deal(post_id: str, conditions=()) -> ParsedDeal:
    return ParsedDeal(site="ppomppu", post_id=post_id, title="t", url="u", headline_price=1000,
                      applied_conditions=list(conditions))


def test_counters_report_yield_per_site():
    result = _result(
        [
            SiteObservation("ppomppu", Outcome.OK, 21),
            SiteObservation("ruliweb", Outcome.OK, 28),
            SiteObservation("fmkorea", Outcome.OK, 20),
        ]
    )

    assert counters(result) == {
        "sites_polled": 3,
        "deals": 69,
        "by_site": {"ppomppu": 21, "ruliweb": 28, "fmkorea": 20},
        "failures": 0,
        "blocked": 0,
        "alerts": 0,
        "conditional": 0,
        "stopped_sites": [],
    }


def test_counters_separate_transient_failures_from_blocks():
    """일시 장애와 차단은 대응이 다르다(백오프 vs 중지). 세는 칸도 달라야 한다."""
    result = _result(
        [
            SiteObservation("ppomppu", Outcome.TRANSIENT, 0),
            SiteObservation("ruliweb", Outcome.BLOCKED, 0),
            SiteObservation("fmkorea", Outcome.OK, 20),
        ],
        alerts=[Alert(site="ruliweb", reason="차단", at=NOW)],
        stopped=("ruliweb",),
    )

    c = counters(result)
    assert c["failures"] == 1
    assert c["blocked"] == 1
    assert c["alerts"] == 1
    assert c["stopped_sites"] == ["ruliweb"]
    assert c["deals"] == 20


def test_counters_of_a_skipped_cycle_are_all_zero():
    """레이트 하한 때문에 아무 사이트도 due가 아니면 관측이 없다."""
    assert counters(_result([]))["sites_polled"] == 0


def test_zero_yield_is_visible_not_hidden():
    """`ok=True, deals=0`은 구조 변경의 전형이다. 0을 로그에서 지우면 안 된다."""
    c = counters(_result([SiteObservation("ppomppu", Outcome.OK, 0)]))

    assert c["by_site"] == {"ppomppu": 0}
    assert c["deals"] == 0


def test_counters_count_conditional_prices():
    """조건부 가격(`카할`·`유료배송(금액미상)`)은 **무조건 가격이 아니다.**

    그 태그는 `raw._derived`까지만 가고 `deal_event`에 도달하지 않는다(docs/91 Q-46) — 즉
    기준가 표본이 조용히 오염된다. 고칠 수 없는 결함은 **세어서 노출한다**. 폴링을 켜는 사람이
    `docker logs`에서 오염률을 바로 본다(골든 실측: 뽐뿌 9.5% · 펨코 15%).
    """
    result = _result(
        [SiteObservation("ppomppu", Outcome.OK, 3)],
        deals=[_deal("1"), _deal("2", ["카할"]), _deal("3", ["유료배송(금액미상)"])],
    )

    assert counters(result)["conditional"] == 2


def test_conditional_zero_is_not_omitted():
    """0을 생략하면 "조건부 0건"과 "안 셌다"가 구별되지 않는다(OBS-02)."""
    result = _result([SiteObservation("ppomppu", Outcome.OK, 1)], deals=[_deal("1")])

    assert counters(result)["conditional"] == 0


def test_counters_serialize_cleanly_as_an_event():
    result = _result([SiteObservation("ppomppu", Outcome.OK, 21)])

    line = event("cycle", NOW + timedelta(seconds=1), **counters(result))

    line.encode("cp949")
    assert json.loads(line)["by_site"] == {"ppomppu": 21}
