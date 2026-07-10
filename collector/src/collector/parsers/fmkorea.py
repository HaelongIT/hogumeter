"""펨코(에펨코리아) 핫딜 리스트 파서(docs/98). 리스트에 쇼핑몰/가격/배송이 구조화(.hotdeal_info)."""

from __future__ import annotations

from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import classify_shipping, normalize_price
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

    # 배송 어휘 해석은 `classify_shipping` 한 곳에만 있다 — 뽐뿌·루리웹의 괄호 자리와 같은 규칙이다.
    # 파서마다 따로 판단하면 한쪽이 모르는 어휘를 조용히 0으로 흡수한다(실제로 그랬다).
    #
    # golden 20딜의 배송 칸: `무료`(17) · `와우무배` · `네멤무료` · `1만5천원무료`.
    # **숫자 배송비는 하나도 없다** — fixture 커버리지 0인 영역이라 합성 케이스로만 지켜진다.
    shipping_text = links[2] if len(links) >= 3 else ""
    shipping, shipping_conditions = classify_shipping(shipping_text)
    conditions = list(normalized.applied_conditions) + shipping_conditions
    return normalized.headline_price + shipping, list(dict.fromkeys(conditions))
