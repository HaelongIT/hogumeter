"""적재 준비 — ParsedDeal → RawDealRecord 계약 매핑 + 배치 멱등(자연키 dedup). 네트워크·DB 없음."""

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from collector.parsers.bunjang import parse_bunjang
from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.models import ParsedDeal
from collector.pipeline.ingest import RawDealRecord, to_raw_records

FIXTURES = Path(__file__).parent / "fixtures"
CAPTURED = datetime(2026, 7, 8, 12, 0, tzinfo=timezone.utc)


def _read(rel: str) -> str:
    return (FIXTURES / rel).read_text(encoding="utf-8")


def test_maps_parsed_deal_to_contract_record():
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"))
    records = to_raw_records(deals, CAPTURED)

    assert len(records) == 20  # 중복 없음
    r = records[0]
    assert isinstance(r, RawDealRecord)
    assert r.site == "fmkorea"
    assert r.post_id == "10041875674"
    assert r.url.endswith("10041875674")
    assert r.headline_price == 13_800
    assert r.status == "ACTIVE"
    assert r.captured_at == CAPTURED


def test_dedupes_by_natural_key_last_wins():
    # 같은 (site, post_id)가 배치에 두 번: ACTIVE→SOLD_OUT로 바뀐 최신 스냅샷이 이긴다
    first = ParsedDeal(site="fmkorea", post_id="1", title="딜", url="u", status="ACTIVE")
    later = ParsedDeal(site="fmkorea", post_id="1", title="딜", url="u", status="SOLD_OUT")

    records = to_raw_records([first, later], CAPTURED)

    assert len(records) == 1
    assert records[0].status == "SOLD_OUT"


def test_rejects_unknown_status():
    bad = ParsedDeal(site="fmkorea", post_id="1", title="딜", url="u", status="WEIRD")

    with pytest.raises(ValueError):
        to_raw_records([bad], CAPTURED)


def test_preserves_raw_payload():
    deal = ParsedDeal(site="bunjang", post_id="9", title="t", url="u", raw={"ad": False, "pid": "9"})

    records = to_raw_records([deal], CAPTURED)

    assert records[0].raw == {"ad": False, "pid": "9"}


def test_accepts_every_status_the_bunjang_parser_can_emit():
    """파서가 내는 status는 계약 허용집합 안이어야 한다.

    `parse_bunjang`이 `ENDED`(= deal_event.status의 값)를 내던 시절엔 여기서 ValueError가 났다.
    부품별 GREEN만으로는 이런 어휘 불일치가 안 잡힌다 — docs/91 Q-41.
    """
    payload = json.dumps(
        {
            "list": [
                {"pid": 1, "name": "판매중", "price": "1000", "status": "0", "update_time": 1_700_000_000},
                {"pid": 2, "name": "판매완료", "price": "2000", "status": "1", "update_time": 1_700_000_000},
            ]
        }
    )
    deals = parse_bunjang(payload)

    records = to_raw_records(deals, CAPTURED)  # ValueError가 나면 계약 위반

    assert [r.status for r in records] == ["ACTIVE", "SOLD_OUT"]
