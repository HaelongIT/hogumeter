"""SEC-07 개인정보 최소화 — golden 전수로 못박는다.

`docs/20`: "중고 판매자 닉네임 등 개인 식별 정보는 표시용 최소한만 저장, 별도 수집·프로파일링 금지."

이 규칙은 지금 **지켜지고 있지만 아무것도 강제하지 않는다.** 번개장터 응답에는 `uid`(판매자 식별자)와
`location`(동 단위 주소)이 들어 있고, `parse_bunjang`은 불리언 세 개만 골라 담는다 — 신중해서 그랬을 뿐
계약이 아니었다. `raw`는 `jsonb`이고 파서 한 줄이면 응답 전체가 들어간다.

그래서 두 가지를 단언한다:
  ① `raw`에 담기는 **키**는 사이트별 허용집합 안에 있다 (새 필드는 의식적으로 추가하게 된다)
  ② 적재 레코드 어디에도 fixture의 **PII 값**이 나타나지 않는다 (키 이름을 바꿔 우회해도 잡힌다)

fixture는 파서 검증용만이 아니라 **하류 규칙의 시험대**다(docs/99).
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import pytest

from collector.parsers.bunjang import parse_bunjang
from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.ppomppu import parse_ppomppu
from collector.parsers.ruliweb import parse_ruliweb
from collector.pipeline.ingest import to_raw_records

FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 7, 10, 12, 0, tzinfo=timezone.utc)

# 사이트별로 `raw`에 담아도 되는 키. 늘리려면 SEC-07을 다시 읽고 늘린다.
# `_derived`는 우리가 만든 파생 데이터다(조건 태그, Q-46) — 원본이 아니다.
ALLOWED_RAW_KEYS = {
    "bunjang": {"ad", "bizseller", "free_shipping", "_derived"},
    "ppomppu": {"_derived"},
    "ruliweb": {"_derived"},
    "fmkorea": {"_derived"},
}

CASES = [
    ("bunjang", "bunjang/find_v2_iphone.json", parse_bunjang),
    ("ppomppu", "ppomppu/list_normal.html", parse_ppomppu),
    ("ruliweb", "ruliweb/list_normal.html", parse_ruliweb),
    ("fmkorea", "fmkorea/list_normal.html", parse_fmkorea),
]


def _deals(relative: str, parse):
    payload = (FIXTURES / relative).read_bytes()
    # 파서마다 인코딩 계약이 다르다(뽐뿌 cp949). 파서 테스트가 이미 그걸 덮으므로 여기선 관대하게 읽는다.
    for encoding in ("utf-8", "cp949"):
        try:
            return parse(payload.decode(encoding), NOW)
        except (UnicodeDecodeError, ValueError):
            continue
    raise AssertionError(f"fixture를 디코딩하지 못했다: {relative}")


@pytest.mark.parametrize(("site", "relative", "parse"), CASES)
def test_raw_carries_only_allowed_keys(site: str, relative: str, parse):
    """`raw`는 jsonb다 — 파서 한 줄이면 응답 전체가 들어간다. 키를 허용집합으로 잠근다."""
    records = to_raw_records(_deals(relative, parse), NOW)

    assert records, f"golden에서 딜을 하나도 못 뽑았다: {relative}"
    for record in records:
        assert set(record.raw) <= ALLOWED_RAW_KEYS[site], (
            f"{site}: 허용되지 않은 raw 키 {set(record.raw) - ALLOWED_RAW_KEYS[site]}"
        )


def test_bunjang_drops_seller_identity_and_address():
    """번개 응답에는 `uid`(판매자 식별자)와 `location`(동 단위 주소)이 실제로 들어 있다.

    키 이름이 아니라 **값**으로 확인한다 — `raw={"u": item["uid"]}`처럼 이름을 바꿔도 잡힌다.
    """
    payload = (FIXTURES / "bunjang/find_v2_iphone.json").read_text(encoding="utf-8")
    items = json.loads(payload)["list"]

    uids = {str(item["uid"]) for item in items if item.get("uid")}
    locations = {item["location"] for item in items if item.get("location")}
    assert uids and locations, "fixture에 uid/location이 없다 — 이 테스트가 아무것도 지키지 않는다"

    records = to_raw_records(parse_bunjang(payload), NOW)
    serialized = json.dumps(
        [
            {"title": r.title, "url": r.url, "post_id": r.post_id, "raw": r.raw}
            for r in records
        ],
        ensure_ascii=False,
    )

    for uid in uids:
        assert uid not in serialized, f"판매자 식별자 {uid}가 적재 레코드에 남았다 (SEC-07)"
    for location in locations:
        assert location not in serialized, "판매자 주소가 적재 레코드에 남았다 (SEC-07)"


def test_tracking_identifiers_are_not_stored():
    """`imp_id`·`tracking`은 광고 추적자다. 개인 식별이 아니어도 "별도 수집" 금지에 해당한다."""
    payload = (FIXTURES / "bunjang/find_v2_iphone.json").read_text(encoding="utf-8")
    imp_ids = {item["imp_id"] for item in json.loads(payload)["list"] if item.get("imp_id")}
    assert imp_ids, "fixture에 imp_id가 없다"

    serialized = json.dumps([r.raw for r in to_raw_records(parse_bunjang(payload), NOW)], ensure_ascii=False)

    assert not any(imp_id in serialized for imp_id in imp_ids)


@pytest.mark.parametrize(("site", "relative", "parse"), CASES)
def test_no_record_field_is_a_person(site: str, relative: str, parse):
    """작성자·판매자 이름을 담을 필드 자체가 없다. `RawDealRecord`에 그런 필드가 생기면 여기서 깨진다."""
    record = to_raw_records(_deals(relative, parse), NOW)[0]

    fields = set(vars(record))
    forbidden = {"author", "nickname", "nick", "seller", "seller_id", "uid", "location", "phone"}
    assert fields & forbidden == set(), f"{site}: 개인 식별 필드가 레코드에 생겼다 {fields & forbidden}"
