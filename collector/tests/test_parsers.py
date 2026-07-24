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


# ── 펨코: 배송 칸의 **숫자 배송비가 조용히 사라진다** ────────────────────
#
# `.hotdeal_info` = [쇼핑몰, 가격, 배송]. golden 20딜의 배송 칸은 `무료`(17) + 조건부(3)뿐이라
# **숫자 배송비가 fixture에 하나도 없다.** 그래서 이 결함은 golden 전수 대조로 잡히지 않는다
# (`docs/99` 2026-07-10: golden은 "이미 본 것"에만 강하다).
#
# BM-02의 저장 기준은 **실결제가 + 배송비**다. 배송비를 안 더하면 표본이 실제보다 낮아지고,
# 태그조차 없어서 아무도 모른다 — `유배`와 달리 "모른다"고 말하지도 않는 **조용한 0**이다.

from collector.pipeline.price import FREE_PRICE, SHIPPING_UNKNOWN  # noqa: E402

_FM_NOW = datetime(2026, 7, 10, 21, 30, tzinfo=timezone.utc)


def _fm_html(price_text: str, shipping_text: str) -> str:
    return f"""
    <div id="content"><div class="fm_best_widget"><ul><li>
      <h3 class="title"><a href="/1234567">테스트 상품</a></h3>
      <div class="hotdeal_info">
        <span><a href="#">쿠팡</a></span>
        <span><a href="#">{price_text}</a></span>
        <span><a href="#">{shipping_text}</a></span>
      </div>
      <span class="regdate">21:10</span>
    </li></ul></div></div>
    """


def _fm_deal(price_text: str, shipping_text: str):
    (deal,) = parse_fmkorea(_fm_html(price_text, shipping_text), _FM_NOW)
    return deal


def test_fmkorea_numeric_shipping_is_added_to_the_price():
    """BM-02: 저장 기준 = 실결제가 + 배송비. 2,500원을 버리면 표본이 그만큼 낮아진다."""
    deal = _fm_deal("10,980원", "2,500원")

    assert deal.headline_price == 13_480
    assert deal.applied_conditions == []  # 금액을 아니까 "미상"이 아니다


def test_fmkorea_numeric_shipping_without_the_won_suffix():
    """배송 칸이 `2,500`으로만 올 수도 있다. 숫자면 배송비다."""
    assert _fm_deal("10,980원", "2,500").headline_price == 13_480


def test_fmkorea_unconditional_free_shipping_adds_nothing_and_tags_nothing():
    deal = _fm_deal("10,980원", "무료")

    assert deal.headline_price == 10_980
    assert deal.applied_conditions == []


def test_fmkorea_conditional_free_shipping_is_a_lower_bound():
    deal = _fm_deal("10,980원", "와우무배")

    assert deal.headline_price == 10_980
    assert deal.applied_conditions == ["조건부무료배송:와우무배", SHIPPING_UNKNOWN]


def test_fmkorea_unparseable_shipping_is_marked_unknown_not_zero():
    """`착불`은 금액을 모른다. 0을 더하고 침묵하면 **조용한 거짓말**이 된다."""
    deal = _fm_deal("10,980원", "착불")

    assert deal.headline_price == 10_980
    assert SHIPPING_UNKNOWN in deal.applied_conditions
    assert "배송비:착불" in deal.applied_conditions


def test_fmkorea_missing_shipping_cell_is_unknown_too():
    """배송 칸이 아예 없으면 무료라고 단정할 수 없다."""
    html = _fm_html("10,980원", "무료").replace(
        '<span><a href="#">무료</a></span>\n      </div>', "</div>")
    (deal,) = parse_fmkorea(html, _FM_NOW)

    assert deal.headline_price == 10_980
    assert SHIPPING_UNKNOWN in deal.applied_conditions


# ── 루리웹 `[종료]` 마커는 제목 바깥에 있다 ────────────────────────────────
#
# 마크업: <div class="title_wrapper">[게임S/W] <span style=...>[종료]</span> <a class="subject_link">제목</a></div>
# 파서는 `.title_wrapper a`의 텍스트만 제목으로 읽으므로 **마커를 절대 볼 수 없다.**
# golden 28딜 중 3건이 `[종료]`인데 전부 ACTIVE로 파싱됐다 — 루리웹 딜은 영원히 닫히지 않고,
# 종료된 딜에 "지금 사라" 알림이 나간다.
#
# 그렇다고 제목 substring으로 잡으면 `특가 종료 임박` 같은 정상 딜을 품절로 오독한다.


def test_ruliweb_golden_has_three_sold_out_deals():
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    sold_out = [d for d in deals if d.status == "SOLD_OUT"]
    assert len(sold_out) == 3, [d.title[:30] for d in sold_out]


def _ruliweb_row(marker_html: str, title: str) -> str:
    return f"""
    <table class="board_list_table"><tr class="table_body normal">
      <td><input class="info_article_id" value="999"></td>
      <td><div class="col_9 text_wrapper"><a>
        <div class="title_wrapper subject relative">[게임S/W]
          {marker_html}
          <a class="subject_link deco" href="https://bbs.ruliweb.com/market/board/1020/read/999">{title}</a>
        </div></a></div></td>
      <td class="recomd"><strong>5</strong></td>
      <td class="time">21:10</td>
    </tr></table>
    """


def test_ruliweb_end_marker_outside_the_anchor_is_seen():
    marker = '<span style="background-color: #ff4444;">[종료]</span>'
    (deal,) = parse_ruliweb(_ruliweb_row(marker, "닌텐도 스위치2 (759,600원/무료)"), NOW)

    assert deal.status == "SOLD_OUT"


def test_ruliweb_the_word_end_inside_the_title_is_not_a_sold_out_marker():
    """`특가 종료 임박`은 아직 살아 있는 딜이다. 오차단은 조용히 표본을 지운다."""
    (deal,) = parse_ruliweb(_ruliweb_row("", "특가 종료 임박! 스위치2 (759,600원/무료)"), NOW)

    assert deal.status == "ACTIVE"


def test_ruliweb_other_end_markers():
    for word in ("품절", "매진", "마감"):
        marker = f'<span style="color:red">[{word}]</span>'
        (deal,) = parse_ruliweb(_ruliweb_row(marker, "스위치2 (759,600원/무료)"), NOW)
        assert deal.status == "SOLD_OUT", word


# ── 뽐뿌 `.end2`(품절) 가지는 golden에서 0번 돈다 ──────────────────────────
#
# fixture에 `.end2`가 하나도 없어 이 분기는 **한 번도 실행된 적이 없다.** 루리웹에서 같은 상황이
# 통째로 죽은 코드였다(마커가 파서가 읽지 않는 자리에 있었다). 셀렉터의 진위는 실 사이트로만
# 확인할 수 있고 그건 정지조건이다(docs/91 Q-19). 그때까지 **분기 자체는 합성으로 잠근다** —
# 리팩터가 조용히 지우지 못하게.


def _ppomppu_row(title_class: str) -> bytes:
    html = f"""
    <table id="revolution_main_table"><tr class="baseList bbs_new1">
      <td class="baseList-numb">717710</td>
      <td><a class="{title_class}" href="view.php?id=ppomppu&no=717710">벨트 (11,800원/무료)</a></td>
      <td class="baseList-rec">3 - 0</td>
      <td class="baseList-time">21:10:11</td>
    </tr></table>
    """
    return html.encode("cp949")


def test_ppomppu_end2_marks_sold_out():
    (deal,) = parse_ppomppu(_ppomppu_row("baseList-title end2").decode("cp949"), NOW)

    assert deal.status == "SOLD_OUT"


def test_ppomppu_without_end2_is_active():
    (deal,) = parse_ppomppu(_ppomppu_row("baseList-title").decode("cp949"), NOW)

    assert deal.status == "ACTIVE"


# ── 루리웹의 "가격 없음" 10건은 무엇인가 (실측 내역) ──────────────────────
#
# `no_price` 카운터가 36%를 가리키면 파서 버그로 오독하기 쉽다. 내역을 못박아 둔다.
#   ① 제목이 `…`로 잘려 가격을 잃은 것 2건 (docs/91 Q-18 — 목록만으로는 못 고친다)
#   ② 무료 상품 4건 — `무료`는 가격 패턴이 아니다(BM-02 AC-3). **0원으로 만들지 않는다**:
#      0원이 분포에 들어가면 기준가를 무너뜨린다.
#   ③ 나머지는 애초에 가격이 없는 글(공지·"가격다양").


def test_free_items_are_priced_at_zero_and_tagged_not_skipped():
    """D-5(2026-07-24): 무료 딜은 스킵이 아니라 가격 0 + FREE_PRICE 태그다.

    예전엔 "0을 값으로 쓰지 않는다"는 이유로 스킵했다 — 그런데 여기 0은 값 없음의 대역이 아니라
    원문이 실제로 말하는 값이다(무료 배포). 스킵하면 이 딜을 영원히 못 본다(절대 원칙 3). core의
    pricingSet이 표본에서 빼는 몫이라, 여기 파서는 지어내지 않고 있는 그대로 낸다.
    """
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    free = [d for d in deals if "무료" in d.title and d.headline_price == 0]
    assert len(free) >= 3
    assert all(FREE_PRICE in d.applied_conditions for d in free)


def test_truncated_titles_sometimes_lose_the_price():
    """목록이 긴 제목을 `...`(ASCII 3점, U+2026 아님)으로 자른다 — `(163,`만 남으면 콤마 패턴이 안 된다.

    **잘림 6건 중 가격을 잃은 것은 2건뿐이다.** 나머지는 가격 뒤에서 잘렸다 — "잘렸으면 가격이 없다"는
    내 첫 단정은 틀렸고 이 테스트가 잡았다. 고칠 수 없는 것은 세어서 노출한다(`no_price`). 글 본문이 필요하다(Q-18).
    """
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    truncated = [d for d in deals if d.title.endswith('...')]
    assert len(truncated) == 6
    assert sum(1 for d in truncated if d.headline_price is None) == 2


def test_ruliweb_no_price_count_is_seven():
    """숫자를 고정한다 — 파서를 고칠 때 이 수가 움직이면 무엇이 바뀌었는지 설명해야 한다.

    2026-07-24 D-5로 10 → 7 갱신: 무료 딜 3건이 스킵(None)에서 가격 0으로 바뀌어 빠졌다.
    """
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"), NOW)

    assert sum(1 for d in deals if d.headline_price is None) == 7


# ── 번개: `free_shipping: false`는 "배송비 0"이 아니라 "금액 미상"이다 ────────
#
# golden 20건 중 12건이 `free_shipping: false`인데 배송비 0을 더하고 태그도 없었다.
# 뽐뿌의 `유배`와 **정확히 같은 부류**다 — 저장된 가격은 실결제가가 아니라 하한이다(BM-02).
#
# `parse_bunjang`은 아직 프로덕션 호출자가 0이다(M2). 죽은 채로 썩지 않게 지금 잠근다.


def test_bunjang_free_shipping_adds_nothing_and_tags_nothing():
    payload = _bunjang_payload(free_shipping=True, price="800000")

    (deal,) = parse_bunjang(payload, NOW)

    assert deal.headline_price == 800_000
    assert deal.applied_conditions == []


def test_bunjang_paid_shipping_of_unknown_amount_is_marked():
    """개인간 거래의 배송비는 응답에 금액이 없다. 0을 더하고 침묵하면 조용한 거짓말이다."""
    payload = _bunjang_payload(free_shipping=False, price="800000")

    (deal,) = parse_bunjang(payload, NOW)

    assert deal.headline_price == 800_000  # 지어내지 않는다
    assert SHIPPING_UNKNOWN in deal.applied_conditions
    assert '유료배송(금액미상)' in deal.applied_conditions


def test_bunjang_golden_marks_every_paid_shipping_item():
    deals = parse_bunjang(_read("bunjang/find_v2_iphone.json"), NOW)

    marked = [d for d in deals if SHIPPING_UNKNOWN in d.applied_conditions]
    assert len(marked) == 12  # golden 20건 중 free_shipping=false가 12건
    assert all(not d.raw['free_shipping'] for d in marked)


def _bunjang_payload(*, free_shipping: bool, price: str) -> str:
    import json as _json

    return _json.dumps({
        "list": [{
            "pid": 1, "name": "아이폰15pro 256 S급", "price": price,
            "update_time": 1783167297, "num_faved": "0", "status": "0",
            "ad": False, "bizseller": False, "free_shipping": free_shipping,
        }]
    })
