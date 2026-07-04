"""번개장터 파서 — 비공식 검색 JSON API 응답(docs/98). HTML 파싱 불요."""

from __future__ import annotations

import json
from datetime import datetime, timezone

from .models import ParsedDeal


def parse_bunjang(payload: str) -> list[ParsedDeal]:
    data = json.loads(payload)
    deals: list[ParsedDeal] = []
    for item in data.get("list", []):
        pid = str(item["pid"])
        price_raw = str(item.get("price", "")).strip()
        deals.append(
            ParsedDeal(
                site="bunjang",
                post_id=pid,
                title=item.get("name", ""),
                url=f"https://m.bunjang.co.kr/products/{pid}",
                reaction_score=int(item.get("num_faved") or 0),
                headline_price=int(price_raw) if price_raw.isdigit() else None,
                posted_at=datetime.fromtimestamp(int(item["update_time"]), tz=timezone.utc),
                status="ACTIVE" if str(item.get("status")) == "0" else "ENDED",
                raw={
                    "ad": item.get("ad", False),
                    "bizseller": item.get("bizseller", False),
                    "free_shipping": item.get("free_shipping", False),
                },
            )
        )
    return deals
