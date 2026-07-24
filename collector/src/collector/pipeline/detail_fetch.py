"""D-6(2026-07-24 확정) — 루리웹 잘린 제목의 가격을 상세 페이지 fetch로 복구할 후보를 고른다.

**전부 상세 fetch(①안)는 사이클당 1 → ~10 요청**(루리웹 기준)이라 원칙 5(저빈도·개인용)와
맞바꿈이 크다. **등록 제품 별칭이 걸리는 잘린 제목만**(②안, 채택)이면 사이클당 대개 0~1건 —
우리가 알림 낼 딜의 가격만 복구하므로 목적에 정확히 부합하고 가장 싸다.

⚠️ **여기까지만 안전하게 지을 수 있다.** 실제 상세 페이지 HTTP fetch·HTML 파싱은 아직 없다 —
루리웹 상세(view) 페이지 구조를 담은 실 fixture가 없어서, 셀렉터를 손으로 지으면 "우연히 옳은
코드"(다음 개편에 조용히 깨짐)가 된다. 이 모듈은 **"무엇을 fetch해야 하는가"만** 정한다 —
그 결정이 서면 실 fetch·파서는 fixture가 오는 대로 별도로 붙인다(docs/91 Q-80).
"""

from __future__ import annotations

from ..parsers.models import ParsedDeal
from .price import is_truncated


def needs_detail_fetch(deal: ParsedDeal, aliases: list[str]) -> bool:
    """가격을 못 읽었고, 제목이 잘렸고, 등록 별칭이 **잘리지 않은(보이는) 부분**에 걸리면 True.

    별칭이 잘려나간 뒷부분에만 있었다면 지금 가진 정보로는 알 길이 없다 — 그건 놓침으로
    받아들인다(추측하지 않는다, 절대 원칙 2). 공백 차이는 표기 차이일 뿐이라 제거하고 비교한다
    (core `TitleNormalizer.joined`와 같은 의도 — 완전히 같은 알고리즘일 필요는 없다, 여기는
    "fetch할 가치가 있는가"라는 보수적 후보 선정이지 최종 매칭이 아니다).
    """
    if deal.headline_price is not None:
        return False
    if not is_truncated(deal.title):
        return False
    normalized_title = deal.title.replace(" ", "")
    return any(alias.replace(" ", "") in normalized_title for alias in aliases if alias)


def deals_needing_detail_fetch(deals: list[ParsedDeal], aliases: list[str]) -> list[ParsedDeal]:
    """이번 사이클에서 상세 fetch 후보인 딜만 골라낸다(순서 보존)."""
    return [deal for deal in deals if needs_detail_fetch(deal, aliases)]
