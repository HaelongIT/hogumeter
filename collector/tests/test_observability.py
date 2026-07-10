"""OBS-01 구조화 로그 · OBS-02 카운터 — 순수 함수(now 주입, IO 없음).

실 폴링을 켜면 사람은 `docker logs`만 본다. 사이클마다 무엇을 몇 건 수집했고 무엇이 실패했는지가
기계로 읽히는 형태여야 한다. 그리고 **한글을 이스케이프해 cp949 콘솔에서도 죽지 않아야 한다** —
em dash 하나로 엔트리포인트가 죽은 적이 있다(docs/99).
"""

import json
from datetime import datetime, timedelta, timezone

from collector.observability import counters, event
from collector.pipeline.price import SHIPPING_UNKNOWN
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
            SiteObservation("ppomppu", Outcome.OK, 21, 21),
            SiteObservation("ruliweb", Outcome.OK, 28, 28),
            SiteObservation("fmkorea", Outcome.OK, 20, 20),
        ]
    )

    assert counters(result) == {
        "sites_polled": 3,
        "deals": 69,
        "by_site": {"ppomppu": 21, "ruliweb": 28, "fmkorea": 20},
        "failures": 0,
        "blocked": 0,
        "alerts": 0,
        "no_price": 0,
        "conditional": 0,
        "shipping_unknown": 0,
        "shipping_unknown_by_site": {"ppomppu": 0, "ruliweb": 0, "fmkorea": 0},
        "sold_out_by_site": {"ppomppu": 0, "ruliweb": 0, "fmkorea": 0},
        "stopped_sites": [],
    }


def test_counters_separate_transient_failures_from_blocks():
    """일시 장애와 차단은 대응이 다르다(백오프 vs 중지). 세는 칸도 달라야 한다."""
    result = _result(
        [
            SiteObservation("ppomppu", Outcome.TRANSIENT, 0, 0),
            SiteObservation("ruliweb", Outcome.BLOCKED, 0, 0),
            SiteObservation("fmkorea", Outcome.OK, 20, 20),
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
    c = counters(_result([SiteObservation("ppomppu", Outcome.OK, 0, 0)]))

    assert c["by_site"] == {"ppomppu": 0}
    assert c["deals"] == 0


def test_counters_count_conditional_prices():
    """조건부 가격(`카할`·`유료배송(금액미상)`)은 **무조건 가격이 아니다.**

    태그는 이제 `raw._derived` → `deal_event.applied_conditions`까지 도달한다(Q-46 절반 해소,
    `PreserveAppliedConditionsUseCase`). 그래도 여기서 세는 이유: 폴링을 켜는 사람이 `docker logs`에서
    비율을 즉시 본다(골든 실측: 뽐뿌 9.5% · 펨코 15%). 화면·알림 표시는 아직 없다.
    """
    result = _result(
        [SiteObservation("ppomppu", Outcome.OK, 3, 3)],
        deals=[_deal("1"), _deal("2", ["카할"]), _deal("3", ["유료배송(금액미상)"])],
    )

    assert counters(result)["conditional"] == 2


def test_conditional_zero_is_not_omitted():
    """0을 생략하면 "조건부 0건"과 "안 셌다"가 구별되지 않는다(OBS-02)."""
    result = _result([SiteObservation("ppomppu", Outcome.OK, 1, 1)], deals=[_deal("1")])

    assert counters(result)["conditional"] == 0


def test_counters_serialize_cleanly_as_an_event():
    result = _result([SiteObservation("ppomppu", Outcome.OK, 21, 21)])

    line = event("cycle", NOW + timedelta(seconds=1), **counters(result))

    line.encode("cp949")
    assert json.loads(line)["by_site"] == {"ppomppu": 21}


def test_shipping_unknown_is_a_strict_subset_of_conditional():
    """배송비를 모른 채 0을 더한 딜만 따로 센다. `카할`은 as-posted로 옳은 값이라 여기 들지 않는다.

    이 수가 0이 아니면 표본이 실제보다 **낮게** 편향돼 있다는 뜻이다(기준가가 내려가 좋은 딜을 놓친다).
    폴링을 켠 사람이 `docker logs`에서 오염률을 바로 본다 — 로그에도 없으면 아무도 모른다.
    """
    result = _result(
        [SiteObservation("ppomppu", Outcome.OK, 3, 3)],
        deals=[
            _deal("a", ["카할"]),
            _deal("b", ["유료배송(금액미상)", SHIPPING_UNKNOWN]),
            _deal("c", ["조건부무료배송:와우무배", SHIPPING_UNKNOWN]),
        ],
    )

    assert counters(result)["conditional"] == 3
    assert counters(result)["shipping_unknown"] == 2  # 카할은 배송비 문제가 아니다


def test_no_price_deals_are_counted_not_swallowed():
    """BM-02 AC-3: 가격 패턴이 없으면 딜을 만들지 않는다. **그 사실이 로그에 보여야 한다.**

    루리웹 golden은 28딜 중 10건(36%)이 가격 없음이다. 세지 않으면 폴링을 켠 사람은
    "표본이 왜 안 쌓이지"를 알 수 없다. 0도 센다(OBS-02).
    """
    result = _result(
        [SiteObservation("ruliweb", Outcome.OK, 3, 3)],
        deals=[_deal("a"), _priceless("b"), _priceless("c")],
    )

    c = counters(result)
    assert c["deals"] == 3  # 수집은 3건
    assert c["no_price"] == 2  # 그중 2건은 딜이 되지 못한다


def test_no_price_zero_is_not_omitted():
    result = _result([SiteObservation("ppomppu", Outcome.OK, 1, 1)], deals=[_deal("1")])

    assert counters(result)["no_price"] == 0


def _priceless(post_id: str) -> ParsedDeal:
    return ParsedDeal(site="ruliweb", post_id=post_id, title="가격 없는 글", url="u",
                      headline_price=None)


def test_shipping_unknown_is_broken_down_by_site():
    """사이트마다 편향이 다르다 — 합산 하나로는 그 사실이 사라진다.

    golden 실측: 번개 60% · 펨코 15% · 루리웹 14.3% · 뽐뿌 4.8%. 폴링을 켠 사람은 "어느 사이트의
    표본이 얼마나 하한인가"를 알아야 사이트 간 기준가를 섞을지 판단할 수 있다.
    **0도 센다** — 어떤 사이트의 0%는 좋은 소식이 아니라 태그가 죽었다는 뜻일 수 있다.
    """
    result = _result(
        [SiteObservation("ppomppu", Outcome.OK, 2, 2), SiteObservation("ruliweb", Outcome.OK, 1, 1)],
        deals=[
            _deal("a", [SHIPPING_UNKNOWN]),
            _deal("b", ["카할"]),
            ParsedDeal(site="ruliweb", post_id="c", title="t", url="u", headline_price=1000),
        ],
    )

    c = counters(result)
    assert c["shipping_unknown_by_site"] == {"ppomppu": 1, "ruliweb": 0}
    assert c["shipping_unknown"] == 1  # 합산도 그대로 낸다


def test_shipping_unknown_by_site_lists_every_polled_site_even_at_zero():
    result = _result([SiteObservation("fmkorea", Outcome.OK, 0, 0)], deals=[])

    assert counters(result)["shipping_unknown_by_site"] == {"fmkorea": 0}


def test_sold_out_is_counted_per_site():
    """품절 표식이 죽어도 golden 테스트는 GREEN이다 — golden은 고정이고 사이트는 변한다.

    **알림이 아니라 카운터로 낸다.** 뽐뿌는 골든에서 `.end2`가 0건이라(Q-19 미검증) 알림으로
    만들면 매 사이클 오알림한다. 사실은 세고 결론은 사람이 낸다(절대 원칙 2).

    `pre-deploy`: 어떤 사이트의 `sold_out`이 며칠째 0이면 그 사이트의 품절 셀렉터를 의심한다 —
    루리웹의 `[종료]`가 정확히 그랬다(제목 앵커 밖에 있어 파서가 볼 수 없었다).
    """
    result = _result(
        [SiteObservation("ruliweb", Outcome.OK, 3, 3), SiteObservation("ppomppu", Outcome.OK, 1, 1)],
        deals=[
            _deal("a"),
            ParsedDeal(site="ruliweb", post_id="b", title="t", url="u", headline_price=1, status="SOLD_OUT"),
            ParsedDeal(site="ruliweb", post_id="c", title="t", url="u", headline_price=1, status="SOLD_OUT"),
        ],
    )

    c = counters(result)
    assert c["sold_out_by_site"] == {"ruliweb": 2, "ppomppu": 0}  # 0도 센다


def test_sold_out_by_site_is_zero_when_nothing_ended():
    result = _result([SiteObservation("fmkorea", Outcome.OK, 1, 1)], deals=[_deal("1")])

    assert counters(result)["sold_out_by_site"] == {"fmkorea": 0}
