"""`ParsedDeal[]`(번개 검색 결과) → `used_listing_observation` **insert-only** 적재(USED-02).

`raw_deal_post`와 달리 업서트가 아니다 — 이 테이블은 "그 시각 목록에 무엇이 있었는가"의 스냅샷이고,
같은 매물이 매 주기 다시 들어오는 것이 **정상**이다. core가 연속된 두 배치를 비교해 생애주기를
도출한다(`FoldUsedListingsUseCase`). 덮어쓰면 그 사이의 가격 변동이 사라진다.

**가격 없는 매물은 넣을 수 없다** — `price`가 `not null`이다. 조용히 버리지 않고 세어서 반환한다
("값을 못 구하면 0을 더하되 그 사실을 값 옆에 실어 보낸다").
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

import psycopg
from psycopg.types.json import Jsonb

from ..parsers.models import ParsedDeal

_INSERT = """
insert into used_listing_observation
    (used_search_id, listing_id, title, price, observed_at, raw)
values
    (%(used_search_id)s, %(listing_id)s, %(title)s, %(price)s, %(observed_at)s, %(raw)s)
"""


@dataclass(frozen=True)
class UsedListingBatchResult:
    """한 배치 적재 결과. 부류가 다른 사실을 한 수로 합치지 않는다."""

    inserted: int
    skipped_no_price: int


@dataclass(frozen=True)
class UsedListingSink:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def insert_batch(
        self, used_search_id: int, deals: list[ParsedDeal], observed_at: datetime
    ) -> UsedListingBatchResult:
        """한 스냅샷을 통째로 넣는다. 배치 안의 중복 `post_id`는 접지 않는다 — core의 diff가
        스냅샷 단위로 dedupe하며(마지막 관측 승리) 여기서 접으면 그 사실이 사라진다."""
        priced = [deal for deal in deals if deal.headline_price is not None]
        skipped = len(deals) - len(priced)
        if priced:
            with self.connection.cursor() as cursor:
                cursor.executemany(
                    _INSERT,
                    [_params(used_search_id, deal, observed_at) for deal in priced],
                )
            self.connection.commit()
        return UsedListingBatchResult(inserted=len(priced), skipped_no_price=skipped)


def _params(used_search_id: int, deal: ParsedDeal, observed_at: datetime) -> dict:
    return {
        "used_search_id": used_search_id,
        "listing_id": deal.post_id,
        "title": deal.title,
        "price": deal.headline_price,
        # SEC-07: `raw` 허용집합은 파서가 이미 좁혔다(uid·location·imp_id 배제). 여기서 넓히지 않는다.
        "raw": Jsonb(deal.raw) if deal.raw else None,
        "observed_at": observed_at,
    }
