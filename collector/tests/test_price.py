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
