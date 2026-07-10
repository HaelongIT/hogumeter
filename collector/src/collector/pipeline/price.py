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

# `만원` 축약. **`만` 뒤에 단위·한글이 붙으면 가격이 아니다.**
#
# 이 패턴만 `_UNIT` 가드가 없었다. 서열 1순위라 뒤의 `_WON`·`_COMMA`·`_BARE`가 가진 가드에
# 닿지도 못한다: `5000만화소` → 50,000,000원(55배), `3만시간 보증` → 30,000원(20배 낮음),
# `2만mAh` → 20,000원. 괄호 관례 `(가격원/배송비)`가 먼저 걸리는 제목에선 드러나지 않았을 뿐이고,
# **루리웹 golden은 28딜 중 22딜이 괄호 없이 온다** — 이 경로가 오히려 정상 경로다.
#
# `원`을 필수로 할 수는 없다. 확정본 경계 케이스가 `89만` → 890,000이다. 그래서 `만` 바로 뒤가
# `원`이거나(`180만원대`), **글자·숫자가 아니어야** 한다(`89만` · `89만 특가`).
# `2만2천원` = 22,000원 — `2만`만 읽으면 10% 낮은 값이 표본에 들어간다.
_MANWON = re.compile(r"(\d+(?:\.\d+)?)\s*만(?:\s*(\d+)\s*천)?(?:\s*원|(?![A-Za-z가-힣0-9]))")
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
#
# **`X카드`는 대부분 상품이다.** `[A-Za-z0-9가-힣]+카드`로 잡으면 그래픽카드·메모리카드·SD카드·
# 기프트카드·교통카드가 전부 "카드할인 조건"이 된다(그래픽카드는 핫딜에서 가장 흔한 품목 중 하나다).
# 오탐은 표본 오염률(`conditional`)을 조용히 부풀리고, 무조건 가격을 "조건부"라고 화면에 쓴다.
#
# 그래서 셋 중 하나여야 한다:
#   ① 발급사 + `카드`가 **붙어 있다** (`신한카드`) — `삼성 메모리카드`는 사이에 공백이 있어 안 걸린다
#   ② 한 글자 자리표시자 + `카드` (`N카드`) — 확정본 AC-2가 쓰는 표기. `SD카드`는 두 글자라 안 걸린다
#   ③ `카드`가 합성명사의 꼬리가 아니고(왼쪽 경계) 뒤에 **할인 문맥**이 온다 (`카드할인`·`카드 결제 시`)
#
# `가`를 문맥에 넣지 않는다 — `그래픽카드가 899,000원`이 "카드 적용가"로 읽힌다. 그 태그 누락은
# 감수한다: **거짓 태그보다 낫다.** 목록에 없는 발급사가 할인 문맥 없이 단독으로 나오는 경우도 놓친다.
# 태그 누락은 하류가 원문 링크로 넘기지만(절대 원칙 6), 거짓 태그는 무조건 가격을 조건부라 말한다.
_CARD_ISSUERS = (
    "KB국민|NH농협|IBK기업|SC제일|카카오뱅크|케이뱅크|우체국|새마을|"
    "신한|국민|롯데|현대|삼성|농협|우리|하나|비씨|씨티|전북|광주|수협|신협|토스|"
    "KB|NH|IBK|BC"
)
_CARD_CONTEXT = "할인|결제|적용"
_NOT_COMPOUND = r"(?<![A-Za-z0-9가-힣])"
_CARD = re.compile(
    rf"(카할"
    rf"|{_NOT_COMPOUND}(?:{_CARD_ISSUERS})카드"
    rf"|{_NOT_COMPOUND}[A-Z]카드"
    rf"|{_NOT_COMPOUND}카드\s*(?:{_CARD_CONTEXT}))"
)
_PAID_SHIPPING_UNKNOWN = "유료배송(금액미상)"

# **안정된 표식.** 배송비를 모른 채 0을 더한 가격은 실결제가가 아니라 **하한**이다.
#
# `카할`(카드할인)과 섞이면 안 된다: 카할은 확정본 AC-2가 허용한 as-posted 값이고(그 카드
# 보유자에겐 정확하다), 이쪽은 BM-02의 저장 기준("실결제가 + 배송비") 자체를 못 지킨 값이다.
# 표본이 실제보다 **낮게** 편향되므로 기준가가 내려가고, 진짜 좋은 딜이 판정을 못 받는다(놓침).
#
# 설명 태그(`유료배송(금액미상)`·`조건부무료배송:와우무배`)는 사람이 읽으라고 있고, 소비처는
# 이 표식 하나만 보면 된다 — 산문을 substring 매칭하게 만들지 않는다. 값을 지어내지 않는 대신
# **모른다는 사실을 값 옆에 실어 보낸다.**
SHIPPING_UNKNOWN = "배송비미상"


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
        total = float(manwon.group(1)) * 10_000
        if manwon.group(2):  # `2만2천원`의 `2천`
            total += int(manwon.group(2)) * 1_000
        return int(round(total))
    for pattern in (_WON, _COMMA, _BARE):
        match = pattern.search(text)
        if match:
            return _to_int(match.group(1))
    return None


def _extract_conditions(text: str) -> list[str]:
    # 카드명·카드할인 등 조건 태그만 보존(역산 금지). 중복 제거·순서 유지.
    # `카드 결제` → `카드결제`: 공백은 표기 차이일 뿐 다른 조건이 아니다(같은 태그가 둘로 갈리지 않게).
    conditions = [re.sub(r"\s+", "", tag) for tag in _CARD.findall(text)]
    if "유배" in text:
        # 설명 + 표식을 함께. 표식만으로는 왜인지 모르고, 설명만으로는 기계가 못 읽는다.
        conditions += [_PAID_SHIPPING_UNKNOWN, SHIPPING_UNKNOWN]
    return list(dict.fromkeys(conditions))


def _to_int(amount: str) -> int:
    return int(amount.replace(",", "").replace("원", "").strip())
