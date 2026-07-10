"""BM-02 가격 정규화 — 실결제가+배송비, as-posted(역산 금지), 가격없음 스킵."""

import pytest

from collector.pipeline.price import normalize_price


# ---- AC-1 실결제가 + 배송비 (무료배송=0으로 합산 통일) ----
def test_adds_shipping_fee_to_price():
    result = normalize_price("아이폰 3만원 + 배송비 3,000원")
    assert result is not None
    assert result.headline_price == 33_000


def test_free_shipping_counts_as_zero():
    result = normalize_price("아이폰 3.3만원 무료배송")
    assert result is not None
    assert result.headline_price == 33_000


# ---- AC-2 as-posted: 카드 조건가 그대로, 태그만 보존, 역산 없음 ----
def test_conditional_card_price_kept_as_posted_with_tag():
    result = normalize_price("아이폰 N카드 할인 시 890,000")
    assert result is not None
    assert result.headline_price == 890_000  # 역산 없이 표기가 그대로
    assert result.applied_conditions == ["N카드"]


# ---- AC-3 가격 패턴 없음 → 스킵(None), 미상과 구분 ----
def test_no_price_pattern_is_skipped():
    assert normalize_price("아이폰 팝니다 문의 주세요") is None


# ---- 경계: 만원 축약("89만"), 원 없는 숫자 ----
@pytest.mark.parametrize(
    "text, expected",
    [
        ("갤럭시 89만", 890_000),
        ("갤럭시 89만원", 890_000),
        ("갤럭시 33000", 33_000),
        ("갤럭시 33000원", 33_000),
        ("갤럭시 890,000원", 890_000),
    ],
)
def test_price_boundary_patterns(text, expected):
    result = normalize_price(text)
    assert result is not None
    assert result.headline_price == expected


# ---- 실 제목 회귀 (fixture 49건 실측, docs/91 Q-18) --------------------------
# 아래는 전부 실제 핫딜 제목이다. 휴리스틱이 무엇을 삼켰는지 라이브 없이 드러났다.


@pytest.mark.parametrize(
    "text, expected",
    [
        # D1: 함량·규격·주파수 숫자를 가격으로 오검출하던 것들.
        # `원`이 붙은 숫자가 맨 숫자를 이겨야 한다.
        ("[롯데온]경남제약 파워 블랙마카 1,000mg*60정 (9,600원/무료)", 9_600),
        ("[네이버]안국약품 콘드로이친 1200 60정,3개+밀크씨슬1개(34,710원/무료)", 34_710),
        # `원`이 없으면 콤마 구분 숫자가 맨 숫자를 이긴다(5600MHz를 삼키지 않는다).
        ("[알리] OLOyDDR5 32GB(16x2)5600MHz 513,000", 513_000),
    ],
)
def test_unit_bearing_numbers_are_not_prices(text, expected):
    result = normalize_price(text)
    assert result is not None
    assert result.headline_price == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        # D2: 핫딜 제목의 `(가격원/배송비)` 관례 — 배송비를 합산해야 한다(BM-02 AC-1).
        ("[지마켓]프로젝트엠 남여 반바지 5종 택1 (13,490원/3,000원)", 16_490),
        ("[스냅스]토이스토리5 포토굿즈 40% 할인 (1,200원~/3,000원)", 4_200),
        # 무료·무배는 0으로 합산(기존 경로).
        ("[옥션]1+1 소가죽 남성 핀 버클 벨트(11,800원/무료)", 11_800),
        ("[옥션]레노마 남성 반소매 셔츠 모음전 (26,980원/무배)", 26_980),
    ],
)
def test_paren_price_shipping_convention(text, expected):
    result = normalize_price(text)
    assert result is not None
    assert result.headline_price == expected


@pytest.mark.parametrize(
    "text, expected",
    [
        # D3: 3자리 가격도 `원`이 붙으면 가격이다.
        ("[던킨] 네이버페이 결제시 아메리카노 900원 (7/4~7)", 900),
        ("[플레이스토어] 스폰지밥: 비키니시 전쟁 150원", 150),
    ],
)
def test_three_digit_price_with_won_suffix(text, expected):
    result = normalize_price(text)
    assert result is not None
    assert result.headline_price == expected


@pytest.mark.parametrize(
    "text",
    [
        "[네이버페이] 라방 5원",  # 1자리 = 적립성 금액, 가격 아님
        "롯데마트 오프라인 매장 스위치2",  # 가격 표기 없음
        "[알리] iem ,amp 등등 가격다양",
        "[플레이스토어]구글플레이패스 신규 1달 (무료/무료)",  # 무료 딜은 스킵 유지(Q-18)
    ],
)
def test_non_price_titles_stay_skipped(text):
    assert normalize_price(text) is None


def test_date_and_quantity_numbers_are_ignored():
    """`(7/31까지)`는 배송비 관례가 아니고, `100매`는 가격이 아니다."""
    result = normalize_price("[한길담]100매 한정 코엑스 아쿠아리움 1인 입장권 (7/31까지) (18,300원/무료)")
    assert result is not None
    assert result.headline_price == 18_300


# ---- 정직성 태그: 조건부 가격·금액 미상 배송비를 조용히 삼키지 않는다 --------
def test_card_discount_abbreviation_is_tagged():
    """`카할` = 카드할인 축약(뽐뿌 관례). 가격은 as-posted, 사실은 태그로 보존."""
    result = normalize_price("[지마켓]삼성 로봇청소기 (카할180만원대/무배)")
    assert result is not None
    assert result.headline_price == 1_800_000
    assert "카할" in result.applied_conditions


def test_paid_shipping_of_unknown_amount_is_tagged():
    """`유배` = 유료배송이나 금액 미상. 0으로 합산하되 미상임을 남긴다."""
    result = normalize_price("[롯데온]폴햄 기본 면반팔 3+1팩 (16,450원/유배)")
    assert result is not None
    assert result.headline_price == 16_450
    assert "유료배송(금액미상)" in result.applied_conditions


# ── 배송비를 모른 채 0을 더한 가격은 "하한"이지 "실결제가"가 아니다 ──────────
#
# BM-02의 저장 기준은 **실결제가 + 배송비**다. `유배`(유료배송 금액미상)는 배송비를 알 수 없어
# 0을 더한다 — 즉 저장된 값은 실제보다 **낮다.** `카할`(카드할인)과는 성질이 완전히 다르다:
# 카할은 확정본이 허용한 as-posted 가격이고(그 카드 보유자에겐 정확하다), 이쪽은 **틀린 값**이다.
#
# 둘이 같은 `applied_conditions` 목록에 문자열로 섞여 있으면 소비처가 구별할 수 없다.
# 안정된 표식(`배송비미상`)을 함께 단다 — 산문을 substring 매칭하게 만들지 않는다.

from collector.pipeline.price import SHIPPING_UNKNOWN  # noqa: E402


def test_paid_shipping_of_unknown_amount_is_marked_as_a_lower_bound():
    result = normalize_price("[롯데온]폴햄 기본 면반팔 3+1팩 (16,450원/유배)")

    assert result.headline_price == 16450  # 배송비를 지어내지 않는다
    assert SHIPPING_UNKNOWN in result.applied_conditions
    assert "유료배송(금액미상)" in result.applied_conditions  # 사람이 읽을 설명도 남긴다


def test_card_discount_is_not_a_shipping_problem():
    """`카할`은 as-posted로 옳은 값이다(확정본 AC-2). 배송비 표식을 달면 안 된다 — 오차단."""
    result = normalize_price("(카할180만원대/무료)")

    assert result.applied_conditions == ["카할"]
    assert SHIPPING_UNKNOWN not in result.applied_conditions


def test_known_paid_shipping_is_added_and_not_marked():
    """금액을 아는 유료배송은 더하면 끝이다. 표식은 '모른다'는 뜻이지 '유료'라는 뜻이 아니다."""
    result = normalize_price("(13,490원/3,000원)")

    assert result.headline_price == 16490
    assert SHIPPING_UNKNOWN not in result.applied_conditions


def test_unconditional_free_shipping_is_not_marked():
    result = normalize_price("(11,800원/무료)")

    assert result.headline_price == 11800
    assert result.applied_conditions == []


# ── `X카드`는 대부분 상품이지 할인 조건이 아니다 ──────────────────────────
#
# `[A-Za-z0-9가-힣]+카드`는 **그래픽카드·메모리카드·SD카드·기프트카드·교통카드**를 전부
# "카드할인 조건"으로 읽는다. 그래픽카드는 핫딜에서 가장 흔한 품목 중 하나다.
# 오탐은 조용히 표본 오염률(`conditional`)을 부풀리고, 무조건 가격을 "조건부"라고 화면에 쓴다.
#
# 차단 장치는 **무엇을 통과시켜야 하는가**를 먼저·더 많이 시험한다(CLAUDE.md).


def _tags(title: str) -> list[str]:
    result = normalize_price(title)
    return result.applied_conditions if result else []


# --- 태그가 붙으면 안 되는 것들 (오탐 = 정직성 위반) ---

def test_graphics_card_is_a_product_not_a_discount():
    assert _tags("[다나와]MSI RTX 5070 그래픽카드 (899,000원/무료)") == []


def test_memory_and_sd_cards_are_products():
    assert _tags("[쿠팡]샌디스크 마이크로SD카드 128GB (12,900원/무료)") == []
    assert _tags("[G마켓]삼성 메모리카드 (9,900원/2,500원)") == []


def test_gift_and_transit_cards_are_products():
    assert _tags("[SSG]스타벅스 기프트카드 5만원권 (45,000원/무료)") == []
    assert _tags("[티몬]교통카드 충전 (10,000원/무료)") == []


def test_a_product_noun_ending_in_card_followed_by_a_particle_is_not_a_price_condition():
    """`그래픽카드가 899,000원` — `카드가`가 "카드 적용가"로 읽히면 안 된다."""
    assert _tags("[다나와]그래픽카드가 899,000원") == []


# --- 태그가 붙어야 하는 것들 (진짜 조건) ---

def test_ppomppu_card_discount_abbreviation():
    assert _tags("[지마켓]삼성 로봇청소기 (카할180만원대/무료)") == ["카할"]


def test_issuer_card_is_a_discount_condition():
    assert _tags("[11번가]LG TV (1,290,000원/무료) 신한카드 할인") == ["신한카드"]
    assert _tags("[SSG]다이슨 (599,000원/무료) 현대카드 청구할인") == ["현대카드"]


def test_card_with_an_explicit_discount_context_is_a_condition():
    """발급사를 안 밝혀도 `카드할인`·`카드 결제 시`는 조건이다(확정본 AC-2의 "N카드 할인")."""
    assert _tags("[옥션]에어팟 (199,000원/무료) 카드할인 적용") == ["카드할인"]
    assert _tags("[11번가]TV (1,000,000원/무료) 카드 결제 시") == ["카드결제"]
