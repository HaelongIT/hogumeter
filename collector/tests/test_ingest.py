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
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"), CAPTURED)
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


def test_applied_conditions_are_preserved_under_a_derived_key():
    """`raw`는 "크롤링 원본 보관 전용"(docs/01)이라, 파생 데이터는 `_derived` 아래에 둔다.

    `raw_deal_post`에 조건 컬럼이 없어 임시로 여기 싣는다 — docs/91 Q-46.
    """
    deal = ParsedDeal(
        site="ppomppu",
        post_id="1",
        title="딜",
        url="u",
        raw={"src": "list"},
        applied_conditions=["카할"],
    )

    (record,) = to_raw_records([deal], CAPTURED)

    assert record.raw["src"] == "list"  # 원본 훼손 없음
    assert record.raw["_derived"]["applied_conditions"] == ["카할"]


def test_no_conditions_means_no_derived_key():
    deal = ParsedDeal(site="ppomppu", post_id="1", title="딜", url="u", raw={"src": "list"})

    (record,) = to_raw_records([deal], CAPTURED)

    assert record.raw == {"src": "list"}


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
    deals = parse_bunjang(payload, CAPTURED)

    records = to_raw_records(deals, CAPTURED)  # ValueError가 나면 계약 위반

    assert [r.status for r in records] == ["ACTIVE", "SOLD_OUT"]


# ── SEC-05 크기 상한 ─────────────────────────────────────────────────
#
# 크롤링 텍스트는 전부 비신뢰 입력이다(docs/20 SEC-05). 상한 없이 `text`·`jsonb`로 흘려보내면
# 페이지 하나가 DB와 메모리를 삼킨다. **자르지 않는다** — 잘린 제목은 정상 제목의 얼굴을 한
# 거짓말이고, 매칭(BM-03)을 조용히 망친다. 거절하고 그 사실을 남긴다.

from collector.pipeline.ingest import MAX_POST_ID, MAX_RAW_BYTES, MAX_TITLE, MAX_URL, oversized


def _deal(**over):
    base = dict(site="ppomppu", post_id="1", title="정상 제목", url="https://x/1")
    base.update(over)
    return ParsedDeal(**base)


def test_golden_sized_deals_all_pass():
    """골든 69건의 실측 최대값(title 62 · url 75 · post_id 11 · raw 57B)은 전부 통과해야 한다."""
    deal = _deal(title="가" * 62, url="https://ppomppu.co.kr/" + "a" * 53, post_id="12345678901")

    assert oversized([deal]) == []
    assert len(to_raw_records([deal], CAPTURED)) == 1


def test_oversized_title_is_rejected_not_truncated():
    deal = _deal(title="가" * (MAX_TITLE + 1))

    (rejection,) = oversized([deal])
    assert (rejection.field, rejection.size, rejection.limit) == ("title", MAX_TITLE + 1, MAX_TITLE)
    assert (rejection.site, rejection.post_id) == ("ppomppu", "1")
    # 잘린 제목이 DB로 흘러들지 않는다 — 아예 없다.
    assert to_raw_records([deal], CAPTURED) == []


def test_oversized_url_and_post_id_are_rejected():
    assert oversized([_deal(url="https://x/" + "a" * MAX_URL)])[0].field == "url"
    assert oversized([_deal(post_id="9" * (MAX_POST_ID + 1))])[0].field == "post_id"


def test_oversized_raw_is_measured_in_bytes_not_characters():
    """한글은 UTF-8에서 3바이트다. 글자 수로 재면 상한이 3배로 뚫린다."""
    deal = _deal(raw={"body": "가" * (MAX_RAW_BYTES // 3)})

    (rejection,) = oversized([deal])
    assert rejection.field == "raw"
    assert rejection.size > MAX_RAW_BYTES


def test_a_single_oversized_deal_does_not_drop_the_whole_batch():
    """놓침 > 오알림(원칙 3). 한 건이 비대해도 나머지 68건은 들어간다."""
    good = _deal(post_id="1")
    bad = _deal(post_id="2", title="가" * (MAX_TITLE + 1))

    records = to_raw_records([good, bad], CAPTURED)

    assert [r.post_id for r in records] == ["1"]
    assert [r.field for r in oversized([good, bad])] == ["title"]


def test_oversized_reports_the_first_offending_field_only():
    """왜 거절됐는지 한 가지 이유만 있으면 된다 — 사람이 원문을 보면 된다."""
    deal = _deal(title="가" * (MAX_TITLE + 1), url="https://x/" + "a" * MAX_URL)

    assert len(oversized([deal])) == 1
