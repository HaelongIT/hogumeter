"""파서 공통 DTO — raw_deal_post 계약(site, post_id) 기준의 스냅샷."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime


@dataclass
class ParsedDeal:
    site: str
    post_id: str
    title: str
    url: str
    reaction_score: int = 0
    headline_price: int | None = None
    posted_at: datetime | None = None
    status: str = "ACTIVE"  # ACTIVE / SOLD_OUT / DELETED
    raw: dict = field(default_factory=dict)  # 사이트별 원본 부가필드(JSONB 보관용)
