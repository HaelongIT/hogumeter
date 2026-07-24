"""D-6 결정 로직 — "이 잘린 제목이 상세 fetch할 가치가 있는가"(순수 함수).

실제 상세 페이지 fetch·파싱은 아직 없다(fixture 부재, docs/91 Q-80) — 여기는 후보 선정만 검증한다.
"""

from datetime import datetime, timezone

from collector.parsers.models import ParsedDeal
from collector.pipeline.detail_fetch import deals_needing_detail_fetch, needs_detail_fetch

NOW = datetime(2026, 7, 24, 12, 0, tzinfo=timezone.utc)
ALIASES = ["스위치2", "아이폰17"]


def _deal(title: str, headline_price: int | None = None, **overrides) -> ParsedDeal:
    defaults = dict(site="ruliweb", post_id="1", title=title, url="https://ruliweb.test/1",
                     headline_price=headline_price)
    return ParsedDeal(**{**defaults, **overrides})


def test_no_fetch_needed_when_the_price_was_already_read():
    deal = _deal("닌텐도 스위치2 (163,000원/무료)", headline_price=163_000)

    assert needs_detail_fetch(deal, ALIASES) is False


def test_no_fetch_needed_when_the_title_is_not_truncated():
    """가격이 없어도 잘리지 않았으면 상세 페이지에 더 볼 게 없다 — 목록이 전부다."""
    deal = _deal("롯데마트 오프라인 매장 스위치2")  # 가격 표기 자체가 없음, 안 잘림

    assert needs_detail_fetch(deal, ALIASES) is False


def test_no_fetch_needed_when_no_registered_alias_matches():
    """등록 제품과 무관한 잘린 딜까지 fetch하면 ①안(전부 fetch)이 된다 — 그건 채택 안 됐다."""
    deal = _deal("무관한 잡화 대량 할인 ...")

    assert needs_detail_fetch(deal, ALIASES) is False


def test_fetch_needed_when_truncated_priceless_and_alias_matches():
    deal = _deal("닌텐도 스위치2 정품 (163,...")

    assert needs_detail_fetch(deal, ALIASES) is True


def test_alias_matching_ignores_spacing_differences():
    """공백은 표기 차이일 뿐이다 — "스위치 2"도 "스위치2" 별칭에 걸려야 한다."""
    deal = _deal("닌텐도 스위치 2 정품 (163,...")

    assert needs_detail_fetch(deal, ALIASES) is True


def test_alias_only_in_the_truncated_tail_is_a_miss_not_a_guess():
    """별칭이 잘려나간 부분에만 있다면 지금 정보로는 모른다 — 지어내지 않고 놓침으로 둔다."""
    deal = _deal("정체불명 특가 상품 안내 ...")  # 실제로는 뒷부분에 "스위치2"가 있었을 수도 있지만 안 보인다

    assert needs_detail_fetch(deal, ALIASES) is False


def test_empty_alias_list_never_triggers_a_fetch():
    deal = _deal("닌텐도 스위치2 정품 (163,...")

    assert needs_detail_fetch(deal, []) is False


def test_deals_needing_detail_fetch_filters_a_batch_preserving_order():
    match = _deal("닌텐도 스위치2 정품 (163,...", post_id="a")
    no_price_but_not_truncated = _deal("스위치2 특가", post_id="b")
    priced = _deal("스위치2 완전판 (500,...", headline_price=500_000, post_id="c")
    unrelated_truncated = _deal("무관한 잡화 ...", post_id="d")

    result = deals_needing_detail_fetch([match, no_price_but_not_truncated, priced, unrelated_truncated],
                                         ALIASES)

    assert result == [match]
