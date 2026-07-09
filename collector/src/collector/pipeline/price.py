"""BM-02 가격 정규화 — 순수 함수(네트워크 없음).

저장 기준 = 실결제가 + 배송비(무료배송=0). 카드·쿠폰 조건가는 as-posted(역산 금지, 태그만 보존).
가격 패턴이 아예 없으면 None을 돌려 "가격없음 스킵"을 표시한다(미상과 구분 — BM-02 AC-3).

핵심은 **후보 서열**이다. "4자리 이상 숫자 = 가격"으로 첫 매치를 취하면 함량·규격·주파수를 삼킨다
(실측: `1,000mg` → 1000, `5600MHz` → 5600, `콘드로이친 1200` → 1200 — docs/91 Q-18). 그래서
가격다움이 높은 순서로 찾는다:

  1. `만원` 축약 (`3.3만원`, `카할180만원대`)
  2. `원`이 붙은 숫자(3자리 이상) — `9,600원`이 `1,000mg`를 이긴다
  3. 콤마 구분 숫자, 뒤에 단위가 붙으면 제외 — `513,000`이 `5600MHz`를 이긴다
  4. 맨 4자리+ 숫자, 단위 제외 — "원 없는 숫자" 폴백

배송비는 핫딜 제목의 `(가격원/배송비)` 관례를 인식한다(뽐뿌·루리웹 공통). 오탐을 막으려 **가격 뒤 `원`을
필수**로 요구한다 — 그래야 `(7/31까지)`·`(7/4~7)`·`(16x2)`가 배송비 표기로 오인되지 않는다.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

# 숫자 뒤에 붙으면 가격이 아니라 규격이다. 긴 것부터(정규식 교대는 앞선 대안이 이긴다).
_UNIT = r"(?:mAh|MHz|GHz|mg|kg|ml|Hz|GB|TB|MB|인치|[gklWV]|정|매|포|롤|캔|봉|팩)"

# 가격 형태: 콤마 구분(1,000) 또는 3자리 이상 연속 숫자.
_AMOUNT = r"(\d{1,3}(?:,\d{3})+|\d{3,})"

_MANWON = re.compile(r"(\d+(?:\.\d+)?)\s*만\s*원?")
_WON = re.compile(rf"(?<![\d,]){_AMOUNT}\s*원")
_COMMA = re.compile(rf"(?<![\d,])(\d{{1,3}}(?:,\d{{3}})+)(?!\s*{_UNIT})")
_BARE = re.compile(rf"(?<![\d,])(\d{{4,}})(?!\s*{_UNIT})")

# `(13,490원/3,000원)` · `(1,200원~/3,000원)` · `(11,800원/무료)` · `(16,450원/유배)`
_PAREN_PRICE_SHIPPING = re.compile(
    rf"\(\s*{_AMOUNT}\s*원\s*~?\s*/\s*(무료배송|무료|무배|유배|[\d,]+\s*원?)\s*\)"
)

_SHIPPING = re.compile(r"배송비\s*([\d,]+)\s*원?")
_FREE_SHIPPING = re.compile(r"무료\s*배송|무배")
# `카할` = 카드할인 축약(뽐뿌 관례). 조건부 가격임을 잃지 않도록 태그로 남긴다.
_CARD = re.compile(r"(카할|[A-Za-z0-9가-힣]+카드)")
_PAID_SHIPPING_UNKNOWN = "유료배송(금액미상)"


@dataclass
class NormalizedPrice:
    """정규화 결과. headline_price = 실결제가 + 배송비."""

    headline_price: int
    applied_conditions: list[str] = field(default_factory=list)


def normalize_price(text: str) -> NormalizedPrice | None:
    conditions = _extract_conditions(text)

    paren = _PAREN_PRICE_SHIPPING.search(text)
    if paren:
        main = _to_int(paren.group(1))
        return NormalizedPrice(main + _shipping_from_token(paren.group(2)), conditions)

    shipping, remaining = _extract_shipping(text)
    main = _extract_main_price(remaining)
    if main is None:
        return None  # AC-3: 가격 패턴 없음 → 스킵(호출자가 스킵 로그)
    return NormalizedPrice(headline_price=main + shipping, applied_conditions=conditions)


def _shipping_from_token(token: str) -> int:
    """`(가격/배송비)`의 배송비 자리. `유배`는 금액 미상이라 0으로 두되 조건 태그가 사실을 보존한다."""
    if _FREE_SHIPPING.search(token) or "무료" in token:
        return 0
    if "유배" in token:
        return 0
    return _to_int(token)


def _extract_shipping(text: str) -> tuple[int, str]:
    """(배송비, 배송비 표현을 제거한 나머지 텍스트). 무료배송·미표기는 0."""
    if _FREE_SHIPPING.search(text):
        return 0, _FREE_SHIPPING.sub("", text)
    match = _SHIPPING.search(text)
    if match:
        value = _to_int(match.group(1))
        return value, text[: match.start()] + text[match.end():]
    return 0, text


def _extract_main_price(text: str) -> int | None:
    """가격다움이 높은 순서로 찾는다. 서열이 곧 오검출 방지책이다."""
    manwon = _MANWON.search(text)
    if manwon:
        return int(round(float(manwon.group(1)) * 10_000))
    for pattern in (_WON, _COMMA, _BARE):
        match = pattern.search(text)
        if match:
            return _to_int(match.group(1))
    return None


def _extract_conditions(text: str) -> list[str]:
    # 카드명·카드할인 등 조건 태그만 보존(역산 금지). 중복 제거·순서 유지.
    conditions = list(_CARD.findall(text))
    if "유배" in text:
        conditions.append(_PAID_SHIPPING_UNKNOWN)
    return list(dict.fromkeys(conditions))


def _to_int(amount: str) -> int:
    return int(amount.replace(",", "").replace("원", "").strip())
