"""엔트리포인트 → 실 Postgres 관통 테스트 (fetch는 fixture, DB는 진짜).

부품별 GREEN은 계약을 보장하지 않는다. `parse_bunjang`이 `ParsedDeal.status="ENDED"`를 내던
사고가 정확히 그랬다 — 파서 테스트도 sink 테스트도 통과했는데, 그 값이 DB의 CHECK 허용집합에
없었다(3일 잠복). 그 사이를 지나는 값은 **아무 테스트도 보지 않았다**.

그래서 여기서는 손으로 만든 레코드가 아니라 **골든 fixture가 실제로 낳는 값 전부**를 실 스키마에
붓는다. 파서·가격 정규화·시각 해석이 낼 수 있는 값의 전 범위가 `raw_deal_post`의 제약
(status CHECK, not null, jsonb, unique)을 통과해야 한다.

네트워크는 나가지 않는다 — opener는 fixture 바이트를 돌려주는 fake다.
"""

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from collector.__main__ import ALLOW_NETWORK_ENV, main
from collector.db.raw_deal_sink import RawDealSink
from collector.scheduler.sites import hotdeal_boards

FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)

# 사이트별 golden. sites.py의 encoding으로 인코딩해 돌려준다(뽐뿌는 cp949).
_GOLDEN = {
    "ppomppu": "ppomppu/list_normal.html",
    "ruliweb": "ruliweb/list_normal.html",
    "fmkorea": "fmkorea/list_normal.html",
}


class GoldenOpener:
    """robots는 허용, 목록 페이지는 golden **원본 바이트**. 실 네트워크는 만지지 않는다.

    바이트를 그대로 준다 — 디코딩은 fetcher가 `SiteSpec.encoding`으로 한다. 여기서 미리 풀면
    "뽐뿌는 cp949"라는 계약을 테스트가 우회해버린다.
    """

    def __init__(self):
        self._pages = {
            spec.url: (FIXTURES / _GOLDEN[spec.name]).read_bytes() for spec in hotdeal_boards()
        }

    def __call__(self, url: str):
        if url.endswith("/robots.txt"):
            return (404, b"")
        return (200, self._pages[url])


@pytest.fixture
def golden_opener():
    return GoldenOpener()


@pytest.mark.integration
def test_every_golden_deal_survives_the_real_schema(monkeypatch, connection, golden_opener, capsys):
    """골든 3사가 낳는 모든 딜이 실 DB 제약을 통과한다 — status CHECK 포함."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    sink = RawDealSink(connection)

    exit_code = main(opener=golden_opener, sink=sink, sleep=lambda _: None, clock=lambda: NOW, max_cycles=1)

    assert exit_code == 0
    cycle = next(e for e in _events(capsys.readouterr().out) if e["event"] == "cycle")
    assert cycle["deals"] > 0 and cycle["written"] == cycle["deals"]  # 파싱한 만큼 다 들어갔다

    with connection.cursor() as cursor:
        cursor.execute("select count(*), count(distinct site) from raw_deal_post")
        rows, sites = cursor.fetchone()
    assert rows == cycle["deals"]
    assert sites == 3  # 3사 모두 실제로 적재됐다(한 사이트만 되는 걸 총합이 가려주지 않게)


@pytest.mark.integration
def test_reingesting_the_same_golden_cycle_changes_nothing(monkeypatch, connection, golden_opener):
    """REL-01: 같은 목록을 두 번 긁어도 행 수·값이 그대로다. captured_at만 갱신된다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    sink = RawDealSink(connection)
    run = lambda now: main(  # noqa: E731
        opener=golden_opener, sink=sink, sleep=lambda _: None, clock=lambda: now, max_cycles=1
    )

    run(NOW)
    before = _snapshot(connection)
    run(NOW + timedelta(minutes=5))  # 레이트 하한(60s)을 넘겨 다시 폴링되게

    after = _snapshot(connection)
    assert len(after) == len(before)
    assert {(r[0], r[1]) for r in after} == {(r[0], r[1]) for r in before}
    assert [r[2:] for r in after] == [r[2:] for r in before]  # title·가격·status 불변


@pytest.mark.integration
def test_korean_titles_and_derived_json_round_trip(monkeypatch, connection, golden_opener):
    """cp949로 디코딩한 뽐뿌 제목이 DB를 왕복해도 온전하고, 조건 태그가 jsonb에 남는다."""
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    main(opener=golden_opener, sink=RawDealSink(connection), sleep=lambda _: None,
         clock=lambda: NOW, max_cycles=1)

    with connection.cursor() as cursor:
        cursor.execute("select title from raw_deal_post where site = 'ppomppu' limit 1")
        (title,) = cursor.fetchone()
        cursor.execute("select count(*) from raw_deal_post where raw -> '_derived' is not null")
        (derived,) = cursor.fetchone()

    assert title.encode("cp949")  # 깨진 문자가 있으면 여기서 터진다
    assert derived > 0  # applied_conditions가 원본 JSON에 보존된다


def _events(out: str) -> list[dict]:
    return [json.loads(line) for line in out.strip().splitlines() if line]


def _snapshot(connection):
    with connection.cursor() as cursor:
        cursor.execute(
            "select site, post_id, title, headline_price, status, posted_at "
            "from raw_deal_post order by site, post_id"
        )
        return cursor.fetchall()


@pytest.mark.integration
def test_sold_out_deals_reach_the_database_as_sold_out(monkeypatch, connection, golden_opener):
    """파서가 낸 `SOLD_OUT`이 **DB까지 간다.** 부품별 GREEN은 이 경로를 보장하지 않는다.

    루리웹의 `[종료]` 마커는 2026-07-10까지 파서가 읽지 않는 자리에 있었다 — golden 28딜 중 3건이
    종료인데 전부 ACTIVE로 적재됐고, 그러면 core가 딜을 ENDED로 닫을 근거가 영원히 없다.
    스모크 5-3은 원문을 직접 `SOLD_OUT`으로 심으므로 **파서를 우회한다** — 여기가 유일한 방어선이다.

    개수를 상수로 못박는 이유: "0이 아니다"는 마커가 하나만 걸려도 통과한다. golden의 실제 분포를 잠근다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    main(opener=golden_opener, sink=RawDealSink(connection), sleep=lambda _: None,
         clock=lambda: NOW, max_cycles=1)

    with connection.cursor() as cursor:
        cursor.execute("""
            select site, status, count(*) from raw_deal_post group by site, status order by site, status
        """)
        rows = {(site, status): count for site, status, count in cursor.fetchall()}

    assert rows[("ruliweb", "SOLD_OUT")] == 3  # `[종료]` 마커 — 제목 앵커 밖의 span
    assert rows[("fmkorea", "SOLD_OUT")] == 2  # `.hotdeal_var8Y`
    assert ("ppomppu", "SOLD_OUT") not in rows  # 이 스냅샷엔 `.end2`가 0건이다(docs/91 Q-19)
    assert rows[("ruliweb", "ACTIVE")] == 25


@pytest.mark.integration
def test_shipping_unknown_tags_reach_the_database(monkeypatch, connection, golden_opener):
    """오늘 고친 것들이 **실 DB까지** 흐르는지 본다 — 파서 GREEN은 적재를 보장하지 않는다.

    골든 실측(2026-07-10): 뽐뿌 1(`유배`) · 펨코 3(조건부 무료배송) · 루리웹 4(제목 잘림) = 8건.
    개수를 상수로 못박는다: "0보다 크다"는 한 건만 걸려도 통과한다.

    이 표식은 core가 `deal_event.applied_conditions`로 옮겨 세고(`shippingUnknownTotal`),
    web이 "실제 결제가는 더 높습니다"를 그린다. 여기서 끊기면 세 모듈이 조용히 0을 본다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    main(opener=golden_opener, sink=RawDealSink(connection), sleep=lambda _: None,
         clock=lambda: NOW, max_cycles=1)

    with connection.cursor() as cursor:
        cursor.execute("""
            select site, count(*)
              from raw_deal_post
             where raw -> '_derived' -> 'applied_conditions' ? '배송비미상'
             group by site order by site
        """)
        by_site = dict(cursor.fetchall())

    assert by_site == {"fmkorea": 3, "ppomppu": 1, "ruliweb": 4}


@pytest.mark.integration
def test_the_cycle_event_reports_the_real_bias_per_site(monkeypatch, connection, golden_opener, capsys):
    """`docker logs`가 폴링을 켠 사람의 **유일한 창**이다. 그 창에 실제 값이 나오는지 본다.

    카운터가 순수 함수로 GREEN이어도, 엔트리포인트가 그것을 이벤트에 싣지 않으면 아무도 못 본다.
    """
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    main(opener=golden_opener, sink=RawDealSink(connection), sleep=lambda _: None,
         clock=lambda: NOW, max_cycles=1)

    cycle = next(e for e in _events(capsys.readouterr().out) if e["event"] == "cycle")

    assert cycle["shipping_unknown"] == 8
    assert cycle["shipping_unknown_by_site"] == {"ppomppu": 1, "ruliweb": 4, "fmkorea": 3}
    assert cycle["no_price"] == 10  # 루리웹 전량(docs/98: 잘림 2 · 무료 4 · 가격 없음 4)
    # `conditional`은 `shipping_unknown`의 **상위집합**이다: 배송비미상 8 + `카할` 1 + `수령:픽업` 1.
    # 픽업은 조건이지만 **배송 문제가 아니다** — 두 카운터가 다른 사실을 말한다는 증거다.
    assert cycle["conditional"] == 10
