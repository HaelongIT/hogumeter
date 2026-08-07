"""번개장터 파서 — 비공식 검색 JSON API 응답(docs/98). HTML 파싱 불요.

status 매핑 주의: 실측된 건 `"0" = 판매중`뿐이고 나머지 코드표는 미측정이다(docs/91 Q-44).
비-"0"을 전부 SOLD_OUT으로 보는 건 **잠정**이며, `예약중`을 판매완료로 오독할 수 있다.
`ParsedDeal.status` 허용집합은 `ACTIVE / SOLD_OUT / DELETED` — `ENDED`는 `deal_event.status`의
값이지 여기 값이 아니다(과거 이 파서가 `ENDED`를 내 `to_raw_records`가 터졌다).
"""

from __future__ import annotations

import json
from datetime import UTC, datetime

from ..pipeline.price import PAID_SHIPPING_UNKNOWN, SHIPPING_UNKNOWN
from .models import ParsedDeal


def parse_bunjang(payload: str, now: datetime | None = None) -> list[ParsedDeal]:
    """`now`는 파서 포트 시그니처를 맞추기 위한 것 — 번개는 `update_time`(epoch)을 직접 준다."""
    data = json.loads(payload)
    deals: list[ParsedDeal] = []
    for item in data.get("list", []):
        # BE-19(코드리뷰 20260806): 다른 필드는 전부 .get()으로 방어하는데 pid·update_time만 직접
        # 인덱싱해, 한 항목의 결측이 KeyError로 루프 전체(정상 항목까지)를 죽였다. 다른 3개 파서와
        # 같은 규율 — 못 찾은 행은 건너뛰고 나머지는 살린다.
        raw_pid = item.get("pid")
        raw_update_time = item.get("update_time")
        if raw_pid is None or raw_update_time is None:
            continue
        pid = str(raw_pid)
        price_raw = str(item.get("price", "")).strip()
        free_shipping = bool(item.get("free_shipping", False))
        deals.append(
            ParsedDeal(
                site="bunjang",
                post_id=pid,
                title=item.get("name", ""),
                url=f"https://m.bunjang.co.kr/products/{pid}",
                reaction_score=int(item.get("num_faved") or 0),
                headline_price=int(price_raw) if price_raw.isdigit() else None,
                posted_at=datetime.fromtimestamp(int(raw_update_time), tz=UTC),
                status="ACTIVE" if str(item.get("status")) == "0" else "SOLD_OUT",
                # `free_shipping: false`는 "배송비 0"이 아니라 **금액 미상**이다 — 응답에 금액이 없다.
                # 뽐뿌의 `유배`와 같은 부류다: 저장된 가격은 실결제가가 아니라 하한이다(BM-02).
                # golden 20건 중 12건이 여기 해당한다. 지어내지 않고 그 사실을 태그로 실어 보낸다.
                applied_conditions=[] if free_shipping else [PAID_SHIPPING_UNKNOWN, SHIPPING_UNKNOWN],
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
