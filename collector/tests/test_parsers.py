"""BM-01 AC-3 파서 golden — fixture HTML/JSON → ParsedDeal 스냅샷. 실 네트워크 호출 금지(문자열 입력)."""

from datetime import datetime, timezone
from pathlib import Path

import pytest

from collector.parsers.bunjang import parse_bunjang
from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.ppomppu import parse_ppomppu
from collector.parsers.ruliweb import parse_ruliweb
from collector.pipeline.timestamps import KST

FIXTURES = Path(__file__).parent / "fixtures"

# 목록 시각("당일 21:10")을 해석하려면 폴링 시각이 필요하다. 2026-07-09 23:00 KST.
NOW = datetime(2026, 7, 9, 14, 0, tzinfo=timezone.utc)


def _read(rel: str) -> str:
    return (FIXTURES / rel).read_text(encoding="utf-8")


def _read_cp949(rel: str) -> str:
    """뽐뿌는 `charset=euc-kr`을 선언하지만 실제로는 cp949 전용 바이트를 담는다(docs/98 실측).
    선언대로 euc-kr로 열면 illegal multibyte sequence로 터진다. 디코딩은 호출자 책임 — 파서는 str만 받는다."""
    return (FIXTURES / rel).read_bytes().decode("cp949")


def test_bunjang_golden_first_item():
    deals = parse_bunjang(_read("bunjang/find_v2_iphone.json"), NOW)

    assert len(deals) == 20
    d = deals[0]
    assert d.post_id == "417956893"
    assert d.title == "아이폰15pro 256 S급"
    assert d.headline_price == 800_000
    assert d.url.endswith("417956893")
    assert int(d.posted_at.timestamp()) == 1_783_167_297
    assert d.reaction_score == 0
    assert d.status == "ACTIVE"
    assert d.raw["ad"] is False


def test_ruliweb_golden_rows():
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    assert len(deals) == 28  # docs/98 실측: info_article_id 28건
    d = deals[0]
    assert d.post_id == "105373"
    assert "빙그레" in d.title
    assert "read/105373" in d.url
    assert d.reaction_score == 3
    assert d.headline_price == 49_560  # 제목 내 가격(BM-02 정규화)


def test_fmkorea_golden_rows():
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"), NOW)

    assert len(deals) == 20  # docs/98 실측: hotdeal_info 20건
    d = deals[0]
    assert d.post_id == "10041875674"
    assert d.title == "더미식 국물요리 350g X 5개 골라담기"
    assert d.headline_price == 13_800  # hotdeal_info 가격+무료배송
    assert d.reaction_score == 0
    assert d.url == "https://www.fmkorea.com/10041875674"
    assert d.status == "ACTIVE"


def test_ppomppu_golden_rows():
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert len(deals) == 21  # docs/98 실측: 뽐뿌게시판 딜 행 21건
    d = deals[0]
    assert d.site == "ppomppu"
    assert d.post_id == "717553"
    assert d.title == "[옥션]1+1 소가죽 남성 핀 버클 벨트(11,800원/무료)"
    assert d.headline_price == 11_800  # 제목 내 가격(BM-02 정규화)
    assert d.reaction_score == 3  # .baseList-rec "3 - 0" = 추천 - 비추천
    assert d.status == "ACTIVE"
    # row0은 인기글이라 `26/07/03`(날짜만) — 시각 미상이므로 23:59 KST (Q-23 잠정값)
    assert d.posted_at == datetime(2026, 7, 3, 23, 59, tzinfo=KST)
    # 나머지 20건은 당일 `HH:MM:SS`
    assert deals[1].posted_at == datetime(2026, 7, 9, 21, 10, 11, tzinfo=KST)


def test_ppomppu_posted_at_feeds_first_seen():
    """core는 `firstSeen = postedAt ?? capturedAt`. postedAt이 없으면 3일 전 글도 '방금 발생'이 된다."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert all(d.posted_at is not None for d in deals)
    assert all(d.posted_at <= NOW for d in deals)  # 미래 발생 시각은 기간 필터를 무너뜨린다


def test_ruliweb_posted_at_handles_both_formats():
    """루리웹은 `날짜 18:10`(당일) / `날짜 2026.07.03`(이전) 두 형식을 섞어 쓴다."""
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    assert deals[0].posted_at == datetime(2026, 7, 9, 18, 10, tzinfo=KST)
    dated = [d for d in deals if d.posted_at == datetime(2026, 7, 3, 23, 59, tzinfo=KST)]
    assert len(dated) == 15  # docs/98 실측
    assert all(d.posted_at is not None for d in deals)


def test_fmkorea_posted_at_is_todays_time():
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"), NOW)

    assert deals[0].posted_at == datetime(2026, 7, 9, 20, 59, tzinfo=KST)
    assert all(d.posted_at is not None for d in deals)


def test_ppomppu_url_is_canonical():
    """href의 page·divpage는 페이지네이션 잔여물 — 자연키 URL은 board+no만."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert deals[0].url == "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=717553"
    assert all("divpage" not in d.url and "page=" not in d.url for d in deals)


def test_ppomppu_excludes_other_board_widgets():
    """목록 페이지엔 뽐뿌마켓(id=pmarket)·자유게시판(id=social) 위젯 행이 섞여 있고,
    이들도 tr.baseList.bbs_new1을 쓴다. 글번호가 없어 자연키를 못 만든다 — 반드시 제외."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert all(d.post_id.isdigit() for d in deals)
    assert all("id=ppomppu" in d.url for d in deals)
    post_ids = [d.post_id for d in deals]
    assert len(post_ids) == len(set(post_ids))  # (site, post_id) 자연키 무결성


def test_ppomppu_missing_recommend_is_zero():
    """신규 글은 .baseList-rec 텍스트가 비어 있다."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert deals[1].post_id == "717718"
    assert deals[1].reaction_score == 0


def test_ppomppu_preserves_conditional_price_tags():
    """BM-02 AC-2: 조건가는 as-posted로 두되 태그로 보존한다.

    태그가 없으면 "누구나 이 가격"인지 "카드할인 적용가"인지 구분할 수 없다.
    """
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    card = next(d for d in deals if d.post_id == "717697")  # (카할180만원대/무배)
    assert card.headline_price == 1_800_000  # 역산하지 않는다
    assert "카할" in card.applied_conditions

    paid_shipping = next(d for d in deals if d.post_id == "717710")  # (16,450원/유배)
    assert "유료배송(금액미상)" in paid_shipping.applied_conditions


def test_ppomppu_unconditional_deal_has_no_tags():
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    assert deals[0].applied_conditions == []  # (11,800원/무료)


@pytest.mark.parametrize(
    "post_id, shipping_word",
    [
        ("10041939805", "와우무배"),  # 쿠팡 와우 회원
        ("10040951722", "네멤무료"),  # 네이버멤버십
        ("10040781360", "1만5천원무료"),  # 일정 금액 이상 구매
    ],
)
def test_fmkorea_tags_conditional_free_shipping(post_id, shipping_word):
    """`.hotdeal_info`의 배송 어휘가 조건을 담는다. 지금까진 전부 '무료 0원'으로 흡수했다."""
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"), NOW)

    deal = next(d for d in deals if d.post_id == post_id)
    assert f"조건부무료배송:{shipping_word}" in deal.applied_conditions


def test_fmkorea_plain_free_shipping_is_not_tagged():
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"), NOW)

    assert deals[0].applied_conditions == []  # 배송 = '무료'


def test_ppomppu_adds_shipping_fee_from_title_convention():
    """제목 관례 `(가격원/배송비)`의 배송비를 합산한다(BM-02 AC-1). 실 fixture가 회귀를 잡는다."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"), NOW)

    d = next(x for x in deals if x.post_id == "717716")
    assert "(13,490원/3,000원)" in d.title
    assert d.headline_price == 16_490  # 13,490 + 3,000
