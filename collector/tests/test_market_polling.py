"""중고 마켓 폴링 종단 배선(USED-02) — 실 core 스키마 위, fixture 응답.

이 테스트가 생기기 전 `UsedSearchSource`·`UsedListingSink`·`market_spec`은 프로덕션 엔트리포인트에서
**아무도 부르지 않았다** — 만들어만 두고 죽은 생산자였다. 이 테스트가 그 경로를 관통해 잠근다.
실 네트워크는 타지 않는다: opener가 번개 fixture를 반환하고, opt-in은 monkeypatch로 켠다.
"""

from __future__ import annotations

import json
import pathlib
from datetime import datetime, timezone

import pytest

from collector.__main__ import ALLOW_NETWORK_ENV, main
from collector.db.used_listing_sink import UsedListingSink
from collector.db.used_search_source import UsedSearchSource

pytestmark = pytest.mark.integration

NOW = datetime(2026, 7, 23, 12, 0, tzinfo=timezone.utc)
FIXTURE = pathlib.Path(__file__).parent / "fixtures/bunjang/find_v2_iphone.json"


class BunjangOpener:
    """robots.txt는 404(부재→허용), 그 외 요청엔 번개 검색 fixture를 준다."""

    def __init__(self, payload: bytes):
        self.payload = payload
        self.calls: list[str] = []

    def __call__(self, url: str):
        self.calls.append(url)
        if url.endswith("/robots.txt"):
            return (404, b"")
        return (200, self.payload)


def _seed_search(connection, required=("아이폰", "256")) -> int:
    with connection.cursor() as cursor:
        cursor.execute(
            "insert into product (name, category, demand_axis_mode) values (%s, %s, %s) returning id",
            ("아이폰 15 Pro", "스마트폰", "GROUPED"),
        )
        product_id = cursor.fetchone()[0]
        cursor.execute(
            """insert into used_search (product_id, platform, required_keywords, exclude_keywords,
                                        target_price, poll_interval_min)
               values (%s, 'BUNJANG', %s, %s, %s, %s) returning id""",
            (product_id, list(required), [], 800_000, 10),
        )
        search_id = cursor.fetchone()[0]
    connection.commit()
    return search_id


def _run(connection, opener, monkeypatch):
    monkeypatch.setenv(ALLOW_NETWORK_ENV, "1")
    return main(
        opener=opener,
        boards=[],  # 게시판은 이 테스트의 관심이 아니다 — 중고 경로만 관통시킨다
        used_sink=UsedListingSink(connection),
        search_source=UsedSearchSource(connection),
        sleep=lambda _: None,
        clock=lambda: NOW,
        max_cycles=1,
    )


def test_registered_search_is_polled_and_listings_land_in_observation(connection, monkeypatch):
    search_id = _seed_search(connection)
    opener = BunjangOpener(FIXTURE.read_bytes())

    exit_code = _run(connection, opener, monkeypatch)

    assert exit_code == 0
    # 검색어가 번개 API로 나갔다(robots.txt 요청은 제외).
    page_calls = [u for u in opener.calls if not u.endswith("/robots.txt")]
    assert any("find_v2.json" in u for u in page_calls), page_calls

    with connection.cursor() as cursor:
        cursor.execute(
            "select count(*) from used_listing_observation where used_search_id = %s", (search_id,)
        )
        assert cursor.fetchone()[0] > 0  # fixture 매물이 관측으로 적재됐다


def test_used_cycle_event_reports_what_was_inserted(connection, monkeypatch, capsys):
    """OBS-02: 조용히 도는 폴링은 안 도는 것과 구별되지 않는다. 적재 수를 이벤트로 낸다."""
    _seed_search(connection)
    opener = BunjangOpener(FIXTURE.read_bytes())

    _run(connection, opener, monkeypatch)

    events = [json.loads(line) for line in capsys.readouterr().out.strip().splitlines() if line]
    used = [e for e in events if e.get("event") == "used_cycle"]
    assert used, "used_cycle 이벤트가 없다 — 마켓 폴링이 배선되지 않았나?"
    assert used[0]["inserted"] > 0
    assert "skipped_no_price" in used[0]  # 0도 실린다


def test_no_registered_search_polls_nothing(connection, monkeypatch):
    """검색이 없으면 번개로 나가지 않는다 — 등록이 폴링의 전제다."""
    opener = BunjangOpener(FIXTURE.read_bytes())

    _run(connection, opener, monkeypatch)

    page_calls = [u for u in opener.calls if not u.endswith("/robots.txt")]
    assert page_calls == []
