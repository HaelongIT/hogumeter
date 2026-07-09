"""뽐뿌게시판 리스트 파서(docs/98). 가격은 제목에 있어 BM-02로 정규화.

입력은 **디코딩된 str**이다. 뽐뿌 응답은 EUC-KR이라 호출자(fetcher)가 디코딩한다.

셀렉터 주의: 오픈소스(krepe90)는 `table#revolution_main_table`에서 행을 찾지만, 우리가 실제로 받는
응답에는 그 id를 가진 요소가 없다(JS 문자열 안에만 등장). UA 위장은 금지(절대 원칙 5)이므로 운영에서도
같은 응답을 받는다 — 그래서 **받는 마크업**을 기준으로 행을 찾는다(docs/98 2026-07-09 정정).

또 하나: 목록 페이지엔 뽐뿌마켓(`id=pmarket`)·자유게시판(`id=social`) 위젯 행이 섞여 있고 이들도
`tr.baseList.bbs_new1`을 쓴다. 글번호가 없어 자연키를 만들 수 없으므로 **게시판 id로 걸러낸다.**
"""

from __future__ import annotations

from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import normalize_price
from ..pipeline.timestamps import parse_board_time
from .models import ParsedDeal

_BOARD = "ppomppu"
_BASE_URL = "https://www.ppomppu.co.kr/zboard/view.php"


def parse_ppomppu(html: str, now: datetime) -> list[ParsedDeal]:
    soup = BeautifulSoup(html, "html.parser")
    deals: list[ParsedDeal] = []
    for tr in soup.select("tr.baseList"):
        if not any(c.startswith("bbs_new") for c in tr.get("class", [])):
            continue  # 공지·광고 행

        anchor = tr.select_one("a.baseList-title")
        if not anchor or f"id={_BOARD}" not in anchor.get("href", ""):
            continue  # 다른 게시판 위젯 행

        post_id = _text(tr.select_one(".baseList-numb"))
        if not post_id.isdigit():
            continue  # 자연키를 만들 수 없는 행

        title = anchor.get_text(strip=True)
        normalized = normalize_price(title)

        deals.append(
            ParsedDeal(
                site=_BOARD,
                post_id=post_id,
                title=title,
                # href의 page·divpage는 페이지네이션 잔여물 — 자연키 URL은 board+no만.
                url=f"{_BASE_URL}?id={_BOARD}&no={post_id}",
                reaction_score=_recommend(tr),
                headline_price=normalized.headline_price if normalized else None,
                # 종료 표식(docs/98). 이 fixture엔 0건이라 실 검증은 미완 — docs/91 Q-19.
                status="SOLD_OUT" if tr.select_one(".end2") else "ACTIVE",
                # `.baseList-time`: 당일 `21:10:11` / 이전 `26/07/03`. 해석엔 "오늘"이 필요하다.
                posted_at=parse_board_time(_text(tr.select_one(".baseList-time")), now),
            )
        )
    return deals


def _recommend(tr) -> int:
    """`.baseList-rec`는 "추천 - 비추천"(예: `3 - 0`). 신규 글은 비어 있다."""
    text = _text(tr.select_one(".baseList-rec"))
    head = text.split("-", 1)[0].strip()
    return int(head) if head.isdigit() else 0


def _text(node) -> str:
    return node.get_text(strip=True) if node else ""
