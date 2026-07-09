"""raw_deal_post 업서트 — core의 실 스키마(V1__init.sql)를 적용한 컨테이너에서 검증.

멱등(REL-01)·상태변화 반영(BM-01 AC-2)·발생시각 불변(C-2)이 이 계약의 전부다.
"""

from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from collector.db.raw_deal_sink import RawDealSink
from collector.parsers.ppomppu import parse_ppomppu
from collector.pipeline.ingest import RawDealRecord, to_raw_records

pytestmark = pytest.mark.integration

CAPTURED = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)
LATER = CAPTURED + timedelta(hours=1)
POSTED = datetime(2026, 7, 8, 21, 10, tzinfo=timezone.utc)


def _record(**overrides) -> RawDealRecord:
    defaults = dict(
        site="ppomppu",
        post_id="717553",
        url="https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=717553",
        title="[옥션]소가죽 벨트",
        captured_at=CAPTURED,
        status="ACTIVE",
        headline_price=11_800,
        posted_at=None,
        reaction_score=3,
        raw={},
    )
    return RawDealRecord(**{**defaults, **overrides})


def _rows(connection, columns: str = "*"):
    with connection.cursor() as cursor:
        cursor.execute(f"select {columns} from raw_deal_post order by site, post_id")
        return cursor.fetchall()


def _count(connection) -> int:
    with connection.cursor() as cursor:
        cursor.execute("select count(*) from raw_deal_post")
        return cursor.fetchone()[0]


def test_inserts_new_records(connection):
    sink = RawDealSink(connection)

    written = sink.upsert_all([_record(post_id="1"), _record(post_id="2"), _record(post_id="3")])

    assert written == 3
    assert _count(connection) == 3


def test_reingesting_the_same_batch_is_idempotent(connection):
    """REL-01: 재실행·중복 폴링에 결과 불변."""
    sink = RawDealSink(connection)
    batch = [_record(post_id="1"), _record(post_id="2")]

    sink.upsert_all(batch)
    before = _rows(connection, "site, post_id, url, title, headline_price, status, captured_at")
    sink.upsert_all(batch)

    assert _count(connection) == 2
    assert _rows(connection, "site, post_id, url, title, headline_price, status, captured_at") == before


def test_sold_out_is_reflected_on_the_existing_row(connection):
    """BM-01 AC-2: 상태 변화는 기존 행에 반영. insert-only면 품절을 영원히 모른다."""
    sink = RawDealSink(connection)
    sink.upsert_all([_record(status="ACTIVE")])

    sink.upsert_all([_record(status="SOLD_OUT", captured_at=LATER)])

    assert _count(connection) == 1
    (status, captured_at), = _rows(connection, "status, captured_at")
    assert status == "SOLD_OUT"
    assert captured_at == LATER


def test_price_and_reaction_changes_are_reflected(connection):
    """core 업서터(refreshFrom)가 놓치던 필드 — 가격변경 전이의 근거가 된다."""
    sink = RawDealSink(connection)
    sink.upsert_all([_record(headline_price=11_800, reaction_score=3)])

    sink.upsert_all([_record(headline_price=9_900, reaction_score=41)])

    (price, reaction), = _rows(connection, "headline_price, reaction_score")
    assert price == 9_900
    assert reaction == 41


def test_posted_at_is_immutable_once_known(connection):
    """C-2: 발생 시각은 불변. 나중 수집이 다른 값을 들고 와도 덮지 않는다."""
    sink = RawDealSink(connection)
    sink.upsert_all([_record(posted_at=POSTED)])

    sink.upsert_all([_record(posted_at=POSTED + timedelta(days=1))])

    (posted_at,), = _rows(connection, "posted_at")
    assert posted_at == POSTED


def test_posted_at_is_filled_in_when_it_was_unknown(connection):
    """처음엔 못 얻었지만(목록에 날짜 없음) 나중에 알게 되면 채운다 — Q-23."""
    sink = RawDealSink(connection)
    sink.upsert_all([_record(posted_at=None)])

    sink.upsert_all([_record(posted_at=POSTED)])

    (posted_at,), = _rows(connection, "posted_at")
    assert posted_at == POSTED


def test_raw_payload_round_trips_as_jsonb(connection):
    sink = RawDealSink(connection)

    sink.upsert_all([_record(raw={"ad": False, "pid": "9", "nested": {"a": 1}})])

    (raw,), = _rows(connection, "raw")
    assert raw == {"ad": False, "pid": "9", "nested": {"a": 1}}


def test_same_post_id_on_different_sites_are_separate_rows(connection):
    """자연키는 (site, post_id)다. 글번호만으로는 충돌하지 않는다."""
    sink = RawDealSink(connection)

    sink.upsert_all([_record(site="ppomppu", post_id="123"), _record(site="ruliweb", post_id="123")])

    assert _count(connection) == 2


def test_status_outside_the_contract_is_rejected_by_the_database(connection):
    """DB CHECK가 최종 방어선이다. 파서가 ENDED를 내던 시절 이걸로 잡혔어야 했다."""
    import psycopg

    sink = RawDealSink(connection)

    with pytest.raises(psycopg.errors.CheckViolation):
        sink.upsert_all([_record(status="ENDED")])


def test_ppomppu_fixture_reaches_the_database_intact(connection):
    """cp949로 디코딩된 제목이 DB까지 온전히 간다 — 종단 계약."""
    fixture = Path(__file__).parent / "fixtures/ppomppu/list_normal.html"
    deals = parse_ppomppu(fixture.read_bytes().decode("cp949"), CAPTURED)
    records = to_raw_records(deals, CAPTURED)

    written = RawDealSink(connection).upsert_all(records)

    assert written == 21
    assert _count(connection) == 21
    with connection.cursor() as cursor:
        cursor.execute("select title from raw_deal_post where post_id = '717553'")
        (title,) = cursor.fetchone()
    assert title.startswith("[옥션]")
    assert "�" not in title
