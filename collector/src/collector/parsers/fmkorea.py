"""펨코(에펨코리아) 핫딜 리스트 파서(docs/98). 리스트에 쇼핑몰/가격/배송이 구조화(.hotdeal_info)."""

from __future__ import annotations

from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import SHIPPING_UNKNOWN, normalize_price
from ..pipeline.timestamps import parse_board_time
from .models import ParsedDeal


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
    """`.hotdeal_info` = [쇼핑몰, 가격, 배송]. **배송 어휘가 조건을 담는다.**

    실측: `무료`(17) / `와우무배`(쿠팡 와우 회원) / `네멤무료`(네이버멤버십) / `1만5천원무료`(금액 조건).
    전부 배송비 0으로 흡수하면 "누구나 이 가격"처럼 보인다 — 조건을 태그로 남긴다(BM-02 AC-2).
    """
    info = li.select_one(".hotdeal_info")
    if not info:
        return None, []
    links = [a.get_text(strip=True) for a in info.select("a")]
    if len(links) < 2:
        return None, []

    price_text = links[1]
    shipping_text = links[2] if len(links) >= 3 else ""
    combined = price_text + (" 무료배송" if "무료" in shipping_text else " " + shipping_text)
    normalized = normalize_price(combined)
    if not normalized:
        return None, []

    conditions = list(normalized.applied_conditions)
    if _is_conditional_free_shipping(shipping_text):
        # 조건부 무료배송에 0을 더했다. 조건을 충족하는지 우리는 모른다 — `와우무배`·`네멤무료`는
        # 멤버십 여부를, `1만5천원무료`는 장바구니 합계를 알아야 한다(실측: 10,980원 딜에 붙어 있었다).
        # 그러므로 이 값은 실결제가가 아니라 **하한**이다. 설명 + 안정된 표식을 함께 단다.
        conditions += [f"조건부무료배송:{shipping_text}", SHIPPING_UNKNOWN]
    return normalized.headline_price, list(dict.fromkeys(conditions))


def _is_conditional_free_shipping(shipping_text: str) -> bool:
    """`무료`만 무조건이다. 그 외에 무료를 뜻하는 표현은 전부 조건이 붙어 있다."""
    if not shipping_text or shipping_text == "무료":
        return False
    return "무료" in shipping_text or "무배" in shipping_text
