"""펨코(에펨코리아) 핫딜 리스트 파서(docs/98). 리스트에 쇼핑몰/가격/배송이 구조화(.hotdeal_info)."""

from __future__ import annotations

import re
from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import SHIPPING_UNKNOWN, normalize_price
from ..pipeline.timestamps import parse_board_time
from .models import ParsedDeal

# 배송 칸이 **순수 금액**일 때만 매치한다(`2,500` · `2,500원`). `fullmatch`로 쓴다 —
# 부분 매치를 허용하면 `1만5천원무료`의 `1`을 배송비로 읽는다.
_SHIPPING_AMOUNT = re.compile(r"([\d,]+)\s*원?")


def parse_fmkorea(html: str, now: datetime) -> list[ParsedDeal]:
    soup = BeautifulSoup(html, "html.parser")
    deals: list[ParsedDeal] = []
    for li in soup.select("#content .fm_best_widget ul li"):
        anchor = li.select_one(".title a")
        if not anchor:
            continue
        href = anchor.get("href", "")
        post_id = href.strip("/").split("/")[-1]
        if not post_id:
            continue
        title = anchor.get_text(strip=True)

        price, conditions = _hotdeal_price(li)
        deals.append(
            ParsedDeal(
                site="fmkorea",
                post_id=post_id,
                title=title,
                url=f"https://www.fmkorea.com/{post_id}",
                reaction_score=_voted_count(li),
                headline_price=price,
                status="SOLD_OUT" if li.select_one(".hotdeal_var8Y") else "ACTIVE",
                posted_at=_posted_at(li, now),  # `.regdate`: 당일 `20:59`
                applied_conditions=conditions,
            )
        )
    return deals


def _posted_at(li, now: datetime):
    node = li.select_one(".regdate")
    return parse_board_time(node.get_text(strip=True), now) if node else None


def _voted_count(li) -> int:
    node = li.select_one(".pc_voted_count .count")
    if not node:
        return 0
    text = node.get_text(strip=True)
    return int(text) if text.isdigit() else 0


def _hotdeal_price(li) -> tuple[int | None, list[str]]:
    """`.hotdeal_info` = [쇼핑몰, 가격, 배송]. 저장 기준은 **실결제가 + 배송비**(BM-02).

    배송 칸을 `normalize_price`에 문자열로 떠넘기지 않는다. 그러면 `2,500원`이 조용히 버려진다 —
    `_extract_shipping`은 `배송비 N원` 관례만 알기 때문이다. 여기서는 배송 칸을 **직접 분류**한다.
    """
    info = li.select_one(".hotdeal_info")
    if not info:
        return None, []
    links = [a.get_text(strip=True) for a in info.select("a")]
    if len(links) < 2:
        return None, []

    normalized = normalize_price(links[1])
    if not normalized:
        return None, []

    shipping_text = links[2] if len(links) >= 3 else ""
    shipping, shipping_conditions = _shipping_of(shipping_text)
    conditions = list(normalized.applied_conditions) + shipping_conditions
    return normalized.headline_price + shipping, list(dict.fromkeys(conditions))


def _shipping_of(text: str) -> tuple[int, list[str]]:
    """배송 칸 → (더할 배송비, 조건 태그).

    실측(golden 20딜): `무료`(17) / `와우무배`(쿠팡 와우 회원) / `네멤무료`(네이버멤버십) /
    `1만5천원무료`(장바구니 금액 조건). **숫자 배송비는 golden에 하나도 없다** — fixture 커버리지가
    0인 영역이라 이 분기는 합성 케이스로만 지켜진다(docs/99 2026-07-10).

    금액을 모르면 **0을 더하되 그 사실을 말한다**(`SHIPPING_UNKNOWN`). 조용히 0을 더하면 표본이
    실제보다 낮아지고 아무도 모른다 — 값 없음을 값으로 표현하지 않는다.
    """
    stripped = text.strip()
    if stripped == "무료":
        return 0, []  # 유일한 무조건 무료

    amount = _SHIPPING_AMOUNT.fullmatch(stripped)
    if amount:
        return int(amount.group(1).replace(",", "")), []  # 금액을 안다 → 더하면 끝

    if _is_conditional_free_shipping(stripped):
        # 조건을 충족하는지 우리는 모른다 — `와우무배`·`네멤무료`는 멤버십 여부를,
        # `1만5천원무료`는 장바구니 합계를 알아야 한다(실측: 10,980원 딜에 붙어 있었다).
        # 그러므로 이 값은 실결제가가 아니라 **하한**이다. 설명 + 안정된 표식을 함께 단다.
        return 0, [f"조건부무료배송:{stripped}", SHIPPING_UNKNOWN]

    if stripped:
        # `착불` 등 해석 못 하는 어휘. 새 어휘가 생겨도 조용히 0이 되지 않는다.
        return 0, [f"배송비:{stripped}", SHIPPING_UNKNOWN]

    # 배송 칸 자체가 없다. "무료"라고 단정할 근거가 없다.
    return 0, [SHIPPING_UNKNOWN]


def _is_conditional_free_shipping(shipping_text: str) -> bool:
    """`무료`만 무조건이다. 그 외에 무료를 뜻하는 표현은 전부 조건이 붙어 있다."""
    if not shipping_text or shipping_text == "무료":
        return False
    return "무료" in shipping_text or "무배" in shipping_text
