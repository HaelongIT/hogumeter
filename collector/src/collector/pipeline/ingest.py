"""적재 준비 — ParsedDeal → raw_deal_post 레코드(계약). 순수 함수(네트워크·DB 없음).

raw_deal_post 계약(docs/01, V1 스키마): (site, post_id) UNIQUE **업서트**. 한 배치 안의 중복은
자연키로 접어(last-wins) 업서트가 배치 내 충돌 없이 삼도록 한다. captured_at은 주입받는다
(폴링/채취 시각 — 순수성 위해 now() 미사용). status는 DB CHECK 제약과 동일 집합을 선검증.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from datetime import datetime

from ..parsers.models import ParsedDeal

_VALID_STATUS = {"ACTIVE", "SOLD_OUT", "DELETED"}

# SEC-05 크기 상한. 크롤링 텍스트는 전부 비신뢰 입력이고, `raw_deal_post`의 컬럼은 무제한 `text`·`jsonb`다.
#
# 값의 근거: golden 89건(핫딜 3사 69 + 번개 20)의 실측 최대는 title 62자 · url 75자 · post_id 11자 ·
# raw 57바이트. 아래 상한은 그보다 넉넉하다 — 정상 글을 거절하는 것(오차단)이 비대한 글 하나를
# 통과시키는 것보다 나쁘기 때문이다. 잠정값이며 seam은 이 상수들 한 곳(Q-55 해소, docs/99 2026-07-10).
MAX_TITLE = 300  # 글자 수. 실측 최대의 약 5배
MAX_URL = 2000  # 실무상 URL 한계
MAX_POST_ID = 64  # 자연키의 절반 — 이보다 길면 사이트 구조 변경을 의심한다
MAX_RAW_BYTES = 256 * 1024  # jsonb 원본 보관. 글자 수가 아니라 **바이트**로 잰다


@dataclass(frozen=True)
class Oversized:
    """상한을 넘겨 적재하지 않은 딜. 왜 버렸는지 남기려고 존재한다(조용한 유실 금지)."""

    site: str
    post_id: str
    field: str
    size: int
    limit: int


def _first_violation(deal: ParsedDeal) -> Oversized | None:
    """첫 위반만 보고한다 — 왜 거절됐는지 이유 하나면 사람이 원문을 볼 수 있다."""
    raw_bytes = len(json.dumps(deal.raw, ensure_ascii=False).encode("utf-8"))
    for name, size, limit in (
        ("title", len(deal.title), MAX_TITLE),
        ("url", len(deal.url), MAX_URL),
        ("post_id", len(deal.post_id), MAX_POST_ID),
        ("raw", raw_bytes, MAX_RAW_BYTES),
    ):
        if size > limit:
            return Oversized(site=deal.site, post_id=deal.post_id, field=name, size=size, limit=limit)
    return None


def oversized(deals: list[ParsedDeal]) -> list[Oversized]:
    """상한을 넘긴 딜들. `to_raw_records`가 버린 것과 정확히 같은 집합이다 — 호출자가 로그로 남긴다."""
    return [violation for deal in deals if (violation := _first_violation(deal)) is not None]


@dataclass(frozen=True)
class RawDealRecord:
    """raw_deal_post 한 행. collector가 `(site, post_id)` 업서트로 적재하는 계약 형태(core가 소비 후 매칭·병합).

    상태·가격 변화는 기존 행에 반영된다(BM-01 AC-2). `posted_at`만 불변(발생 시각, C-2).
    """

    site: str
    post_id: str
    url: str
    title: str
    captured_at: datetime
    status: str
    headline_price: int | None = None
    posted_at: datetime | None = None
    reaction_score: int | None = None
    raw: dict = field(default_factory=dict)


def to_raw_records(deals: list[ParsedDeal], captured_at: datetime) -> list[RawDealRecord]:
    """파싱 결과 배치를 raw_deal_post 레코드로 변환. (site, post_id) 중복은 last-wins로 접는다.

    dict가 키의 첫 등장 위치는 보존하고 값만 마지막으로 갱신하므로, 최신 스냅샷(예: ACTIVE→SOLD_OUT
    전이)이 이긴다. 알 수 없는 status는 ValueError로 조기 실패(DB 도달 전 차단).

    상한(SEC-05)을 넘긴 딜은 **자르지 않고 버린다** — 잘린 제목은 정상 제목의 얼굴을 한 거짓말이라
    매칭(BM-03)을 조용히 망친다. 한 건이 비대해도 배치 전체를 버리지 않는다(원칙 3: 놓침 > 오알림).
    무엇을 왜 버렸는지는 `oversized()`로 조회해 호출자가 로그에 남긴다.
    """
    by_key: dict[tuple[str, str], RawDealRecord] = {}
    for deal in deals:
        if deal.status not in _VALID_STATUS:
            raise ValueError(f"알 수 없는 status: {deal.status!r} ({deal.site}/{deal.post_id})")
        if _first_violation(deal) is not None:
            continue
        by_key[(deal.site, deal.post_id)] = RawDealRecord(
            site=deal.site,
            post_id=deal.post_id,
            url=deal.url,
            title=deal.title,
            captured_at=captured_at,
            status=deal.status,
            headline_price=deal.headline_price,
            posted_at=deal.posted_at,
            reaction_score=deal.reaction_score,
            raw=_raw_with_derived(deal),
        )
    return list(by_key.values())


def _raw_with_derived(deal: ParsedDeal) -> dict:
    """`raw`는 "크롤링 원본 보관 전용"(docs/01)이므로 파생 데이터는 `_derived` 아래로 분리한다.

    조건 태그(BM-02 AC-2)를 담을 컬럼이 `raw_deal_post`에 없어 임시로 여기 싣는다 — docs/91 Q-46.
    """
    if not deal.applied_conditions:
        return deal.raw
    return {**deal.raw, "_derived": {"applied_conditions": list(deal.applied_conditions)}}
