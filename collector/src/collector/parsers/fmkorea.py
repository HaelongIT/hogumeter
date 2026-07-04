"""펨코(에펨코리아) 핫딜 리스트 파서(docs/98). 리스트에 쇼핑몰/가격/배송이 구조화(.hotdeal_info)."""

from __future__ import annotations

from bs4 import BeautifulSoup

from ..pipeline.price import normalize_price
from .models import ParsedDeal


def parse_fmkorea(html: str) -> list[ParsedDeal]:
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

        deals.append(
            ParsedDeal(
                site="fmkorea",
                post_id=post_id,
                title=title,
                url=f"https://www.fmkorea.com/{post_id}",
                reaction_score=_voted_count(li),
                headline_price=_hotdeal_price(li),
                status="SOLD_OUT" if li.select_one(".hotdeal_var8Y") else "ACTIVE",
            )
        )
    return deals


def _voted_count(li) -> int:
    node = li.select_one(".pc_voted_count .count")
    if not node:
        return 0
    text = node.get_text(strip=True)
    return int(text) if text.isdigit() else 0


def _hotdeal_price(li) -> int | None:
    info = li.select_one(".hotdeal_info")
    if not info:
        return None
    links = [a.get_text(strip=True) for a in info.select("a")]
    # links = [쇼핑몰, 가격, 배송]
    if len(links) < 2:
        return None
    price_text = links[1]
    shipping_text = links[2] if len(links) >= 3 else ""
    combined = price_text + (" 무료배송" if "무료" in shipping_text else " " + shipping_text)
    normalized = normalize_price(combined)
    return normalized.headline_price if normalized else None
