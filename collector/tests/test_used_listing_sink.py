"""중고 검색 읽기 + 관측 적재 (실 core 스키마 위의 통합 테스트, USED-02).

이 테스트가 생기기 전 `used_listing_observation`은 **읽는 쪽만** 있었다(core의 `FoldUsedListingsUseCase`).
읽기만 하는 테이블은 죽은 테이블이다 — 생산자를 만들고 그 계약을 여기서 잠근다.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from collector.db.used_listing_sink import UsedListingSink
from collector.db.used_search_source import UsedSearchSource
from collector.parsers.models import ParsedDeal

pytestmark = pytest.mark.integration

T1 = datetime(2026, 7, 1, tzinfo=timezone.utc)
T2 = T1 + timedelta(minutes=10)


def _seed_search(connection, required=("아이폰17", "256")) -> int:
    with connection.cursor() as cursor:
        cursor.execute(
            "insert into product (name, category, demand_axis_mode) values (%s, %s, %s) returning id",
            ("아이폰 17", "스마트폰", "GROUPED"),
        )
        product_id = cursor.fetchone()[0]
        cursor.execute(
            """insert into used_search (product_id, platform, required_keywords, exclude_keywords,
                                        target_price, poll_interval_min)
               values (%s, 'BUNJANG', %s, %s, %s, %s) returning id""",
            (product_id, list(required), ["케이스"], 700_000, 10),
        )
        search_id = cursor.fetchone()[0]
    connection.commit()
    return search_id


def _deal(post_id: str, title: str, price: int | None) -> ParsedDeal:
    return ParsedDeal(
        site="bunjang",
        post_id=post_id,
        title=title,
        url=f"https://m.bunjang.co.kr/products/{post_id}",
        reaction_score=0,
        headline_price=price,
        posted_at=T1,
        status="ACTIVE",
        applied_conditions=[],
        raw={"ad": False, "bizseller": False, "free_shipping": True},
    )


def test_used_search_is_read_from_db_with_its_query(connection):
    """collector가 **무엇을 검색할지 DB에서 받아온다**(Q-72가 없다고 적어 둔 경로)."""
    _seed_search(connection, required=("아이폰17", "256"))

    searches = UsedSearchSource(connection).all_searches()

    assert len(searches) == 1
    assert searches[0].platform == "BUNJANG"
    assert searches[0].required_keywords == ["아이폰17", "256"]
    assert searches[0].query == "아이폰17 256"
    assert searches[0].poll_interval_min == 10


def test_snapshot_is_inserted_not_upserted(connection):
    """같은 매물이 매 주기 다시 들어오는 것이 정상이다 — 덮어쓰면 그 사이의 변동이 사라진다."""
    search_id = _seed_search(connection)
    sink = UsedListingSink(connection)

    sink.insert_batch(search_id, [_deal("b1", "아이폰 17 256 미개봉", 900_000)], T1)
    sink.insert_batch(search_id, [_deal("b1", "아이폰 17 256 미개봉", 820_000)], T2)

    with connection.cursor() as cursor:
        cursor.execute(
            "select price, observed_at from used_listing_observation "
            "where used_search_id = %s and listing_id = 'b1' order by observed_at",
            (search_id,),
        )
        rows = cursor.fetchall()
    assert [row[0] for row in rows] == [900_000, 820_000]  # 두 관측이 **둘 다** 남는다
    assert [row[1] for row in rows] == [T1, T2]


def test_priceless_listing_is_skipped_and_counted_not_silently_dropped(connection):
    """`price`는 not null이라 넣을 수 없다. 조용히 버리지 않고 그 사실을 값 옆에 실어 보낸다."""
    search_id = _seed_search(connection)

    result = UsedListingSink(connection).insert_batch(
        search_id,
        [_deal("b1", "아이폰 17 256", 900_000), _deal("b2", "아이폰 17 가격문의", None)],
        T1,
    )

    assert result.inserted == 1
    assert result.skipped_no_price == 1


def test_empty_batch_touches_nothing(connection):
    search_id = _seed_search(connection)

    result = UsedListingSink(connection).insert_batch(search_id, [], T1)

    assert result == type(result)(inserted=0, skipped_no_price=0)


def test_duplicate_ids_in_one_snapshot_are_kept_for_core_to_fold(connection):
    """배치 안의 중복을 collector가 접지 않는다 — 접으면 '중복이 있었다'는 사실이 사라진다.
    dedupe 규약(마지막 관측 승리)은 core의 `ListingDiff`가 스냅샷 단위로 소유한다."""
    search_id = _seed_search(connection)

    UsedListingSink(connection).insert_batch(
        search_id,
        [_deal("b1", "아이폰 17 256", 900_000), _deal("b1", "아이폰 17 256", 890_000)],
        T1,
    )

    with connection.cursor() as cursor:
        cursor.execute(
            "select count(*) from used_listing_observation where listing_id = 'b1'"
        )
        assert cursor.fetchone()[0] == 2


def test_raw_keeps_the_parser_allowlist_and_nothing_more(connection):
    """SEC-07: 판매자 식별자·주소·광고 추적자는 파서가 이미 뺐다. 적재가 넓히지 않는다."""
    search_id = _seed_search(connection)

    UsedListingSink(connection).insert_batch(search_id, [_deal("b1", "아이폰 17", 900_000)], T1)

    with connection.cursor() as cursor:
        cursor.execute("select raw from used_listing_observation where listing_id = 'b1'")
        raw = cursor.fetchone()[0]
    assert set(raw) == {"ad", "bizseller", "free_shipping"}
