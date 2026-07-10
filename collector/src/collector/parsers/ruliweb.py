"""루리웹 유저 예판 핫딜 리스트 파서(docs/98 셀렉터). 가격은 제목에 있어 BM-02로 정규화.

⚠️ 목록은 긴 제목을 `…`로 자른다 — 잘린 부분에 가격이 있으면 검출되지 않는다(docs/91 Q-18).
"""

from __future__ import annotations

from datetime import datetime

from bs4 import BeautifulSoup

from ..pipeline.price import normalize_price
from ..pipeline.timestamps import parse_board_time
from .models import ParsedDeal

# 종료 마커는 **제목 바깥**에 있다(실측, docs/98):
#
#   <div class="title_wrapper">[게임S/W] <span style="…">[종료]</span> <a class="subject_link">제목</a></div>
#
# 파서는 `.title_wrapper a`의 텍스트만 제목으로 읽으므로 마커를 볼 수 없었다 — golden 28딜 중 3건이
# `[종료]`인데 전부 ACTIVE로 파싱됐다. 그러면 루리웹 딜은 영원히 닫히지 않고(원문이 SOLD_OUT이 아니면
# `ReprocessDealStatusUseCase`가 ENDED로 못 바꾼다), 종료된 딜에 "지금 사라" 알림이 나간다.
#
# 그래서 **앵커 밖 텍스트**만 본다. 제목 안의 `종료`(예: `특가 종료 임박`)는 마커가 아니다 —
# 그걸 품절로 읽으면 살아 있는 딜이 조용히 표본에서 사라진다(오차단).
_SOLD_OUT_MARKERS = ("[품절]", "[종료]", "[매진]", "[마감]")


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
        status = "SOLD_OUT" if _has_end_marker(tr) else "ACTIVE"

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
                applied_conditions=normalized.applied_conditions if normalized else [],
            )
        )
    return deals


def _posted_at(tr, now: datetime):
    node = tr.select_one(".time")
    return parse_board_time(node.get_text(strip=True), now) if node else None


def _to_int(text: str) -> int:
    digits = text.replace(",", "").strip()
    return int(digits) if digits.isdigit() else 0


def _has_end_marker(tr) -> bool:
    """`.title_wrapper`의 **앵커 밖 텍스트**에 `[종료]` 계열 마커가 있는가.

    마커 span에는 클래스가 없고 인라인 스타일뿐이라 셀렉터로 못 집는다. 그래서 위치로 가른다 —
    제목은 **제목 앵커**(`a.subject_link`) 안에 있고 마커는 그 밖에 있다. 이 구분이 오차단
    (제목 속 `특가 종료 임박`)을 막는다. `title_wrapper` 전체가 또 다른 `<a>`로 감싸여 있으므로
    "앵커 밖"이라는 기준은 통하지 않는다 — **제목 앵커** 밖이어야 한다(실측 2026-07-10).

    구조 변경 시 이 함수는 **예외가 아니라 침묵**으로 실패한다(항상 ACTIVE). REL-06 드리프트
    감지는 딜 수가 0이 될 때만 도는데, 여기선 딜 수가 그대로다 — 그래서 golden 테스트가 유일한 방어선이다.
    """
    wrapper = tr.select_one(".title_wrapper")
    if not wrapper:
        return False
    title_anchor = wrapper.select_one("a")  # 파서가 제목으로 읽는 바로 그 앵커
    outside = "".join(
        text
        for text in wrapper.find_all(string=True)
        if title_anchor is None or title_anchor not in text.parents
    )
    return any(marker in outside for marker in _SOLD_OUT_MARKERS)
