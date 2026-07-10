"""번개장터 파서 — 비공식 검색 JSON API 응답(docs/98). HTML 파싱 불요.

status 매핑 주의: 실측된 건 `"0" = 판매중`뿐이고 나머지 코드표는 미측정이다(docs/91 Q-44).
비-"0"을 전부 SOLD_OUT으로 보는 건 **잠정**이며, `예약중`을 판매완료로 오독할 수 있다.
`ParsedDeal.status` 허용집합은 `ACTIVE / SOLD_OUT / DELETED` — `ENDED`는 `deal_event.status`의
값이지 여기 값이 아니다(과거 이 파서가 `ENDED`를 내 `to_raw_records`가 터졌다).
"""

from __future__ import annotations

import json
from datetime import datetime, timezone

from .models import ParsedDeal


def parse_bunjang(payload: str, now: datetime | None = None) -> list[ParsedDeal]:
    """`now`는 파서 포트 시그니처를 맞추기 위한 것 — 번개는 `update_time`(epoch)을 직접 준다."""
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
                status="ACTIVE" if str(item.get("status")) == "0" else "SOLD_OUT",
                # SEC-07 개인정보 최소화: 응답에는 `uid`(판매자 식별자)·`location`(동 단위 주소)·
                # `imp_id`(광고 추적자)도 온다. **담지 않는다.** `raw`는 jsonb라 `item`을 통째로
                # 넣기 쉬우므로, 허용집합을 `tests/test_privacy.py`가 golden 전수로 잠근다.
                raw={
                    "ad": item.get("ad", False),
                    "bizseller": item.get("bizseller", False),
                    "free_shipping": item.get("free_shipping", False),
                },
            )
        )
    return deals
