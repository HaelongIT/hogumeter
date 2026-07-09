"""루리웹 유저 예판 핫딜 리스트 파서(docs/98 셀렉터). 가격은 제목에 있어 BM-02로 정규화.

⚠️ 목록은 긴 제목을 `…`로 자른다 — 잘린 부분에 가격이 있으면 검출되지 않는다(docs/91 Q-18).
"""

from __future__ import annotations

from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import normalize_price
from ..pipeline.timestamps import parse_board_time
from .models import ParsedDeal

_SOLD_OUT_KEYWORDS = ("품절", "종료", "매진", "마감")


def parse_ruliweb(html: str, now: datetime) -> list[ParsedDeal]:
    soup = BeautifulSoup(html, "html.parser")
    deals: list[ParsedDeal] = []
    for tr in soup.select("table.board_list_table tr.table_body.normal"):
        classes = set(tr.get("class", []))
        if classes & {"best", "notice"}:
            continue
        article_id = tr.select_one(".info_article_id")
        if not article_id or not article_id.get("value"):
            continue

        anchor = tr.select_one(".title_wrapper a")
        title = anchor.get_text(strip=True) if anchor else ""
        url = anchor.get("href", "") if anchor else ""

        recomd = tr.select_one(".recomd > strong")
        reaction = _to_int(recomd.get_text(strip=True)) if recomd else 0

        normalized = normalize_price(title)
        status = "SOLD_OUT" if any(k in title for k in _SOLD_OUT_KEYWORDS) else "ACTIVE"

        deals.append(
            ParsedDeal(
                site="ruliweb",
                post_id=article_id["value"],
                title=title,
                url=url,
                reaction_score=reaction,
                headline_price=normalized.headline_price if normalized else None,
                status=status,
                # `.time`: 당일 `날짜 18:10` / 이전 `날짜 2026.07.03` (스크린리더 라벨 접두)
                posted_at=_posted_at(tr, now),
            )
        )
    return deals


def _posted_at(tr, now: datetime):
    node = tr.select_one(".time")
    return parse_board_time(node.get_text(strip=True), now) if node else None


def _to_int(text: str) -> int:
    digits = text.replace(",", "").strip()
    return int(digits) if digits.isdigit() else 0
