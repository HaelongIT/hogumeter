"""BM-02 가격 정규화 — 순수 함수(네트워크 없음).

저장 기준 = 실결제가 + 배송비(무료배송=0). 카드·쿠폰 조건가는 as-posted(역산 금지, 태그만 보존).
가격 패턴이 아예 없으면 None을 돌려 "가격없음 스킵"을 표시한다(미상과 구분 — BM-02 AC-3).

파싱은 규칙 기반 휴리스틱이다. 4자리 이상 숫자를 가격 후보로 보므로 연도·모델번호 등의
오검출 여지가 있다(docs/91 Q-18). 1차 검증 후 정교화.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

_MANWON = re.compile(r"(\d+(?:\.\d+)?)\s*만\s*원?")
_NUMBER = re.compile(r"(\d{1,3}(?:,\d{3})+|\d{4,})\s*원?")
_SHIPPING = re.compile(r"배송비\s*([\d,]+)\s*원?")
_FREE_SHIPPING = re.compile(r"무료\s*배송|무배")
_CARD = re.compile(r"([A-Za-z0-9가-힣]+카드)")


@dataclass
class NormalizedPrice:
    """정규화 결과. headline_price = 실결제가 + 배송비."""

    headline_price: int
    applied_conditions: list[str] = field(default_factory=list)


def normalize_price(text: str) -> NormalizedPrice | None:
    conditions = _extract_conditions(text)
    shipping, remaining = _extract_shipping(text)
    main = _extract_main_price(remaining)
    if main is None:
        return None  # AC-3: 가격 패턴 없음 → 스킵(호출자가 스킵 로그)
    return NormalizedPrice(headline_price=main + shipping, applied_conditions=conditions)


def _extract_shipping(text: str) -> tuple[int, str]:
    """(배송비, 배송비 표현을 제거한 나머지 텍스트). 무료배송·미표기는 0."""
    if _FREE_SHIPPING.search(text):
        return 0, _FREE_SHIPPING.sub("", text)
    match = _SHIPPING.search(text)
    if match:
        value = int(match.group(1).replace(",", ""))
        return value, text[: match.start()] + text[match.end():]
    return 0, text


def _extract_main_price(text: str) -> int | None:
    manwon = _MANWON.search(text)
    if manwon:
        return int(round(float(manwon.group(1)) * 10_000))
    number = _NUMBER.search(text)
    if number:
        return int(number.group(1).replace(",", ""))
    return None


def _extract_conditions(text: str) -> list[str]:
    # 카드명 등 조건 태그만 보존(역산 금지). 중복 제거·순서 유지.
    return list(dict.fromkeys(_CARD.findall(text)))
