"""적재 준비 — ParsedDeal → raw_deal_post 레코드(계약). 순수 함수(네트워크·DB 없음).

raw_deal_post 계약(docs/01, V1 스키마): (site, post_id) UNIQUE 멱등. 한 배치 안의 중복은
자연키로 접어(last-wins) insert-only 적재가 배치 내 충돌 없이 삼도록 한다. captured_at은 주입받는다
(폴링/채취 시각 — 순수성 위해 now() 미사용). status는 DB CHECK 제약과 동일 집합을 선검증.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

from ..parsers.models import ParsedDeal

_VALID_STATUS = {"ACTIVE", "SOLD_OUT", "DELETED"}


@dataclass(frozen=True)
class RawDealRecord:
    """raw_deal_post 한 행. collector가 insert-only로 적재하는 계약 형태(core가 소비 후 매칭·병합)."""

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
    """
    by_key: dict[tuple[str, str], RawDealRecord] = {}
    for deal in deals:
        if deal.status not in _VALID_STATUS:
            raise ValueError(f"알 수 없는 status: {deal.status!r} ({deal.site}/{deal.post_id})")
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
