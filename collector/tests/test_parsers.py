"""BM-01 AC-3 파서 golden — fixture HTML/JSON → ParsedDeal 스냅샷. 실 네트워크 호출 금지(문자열 입력)."""

from pathlib import Path

from collector.parsers.bunjang import parse_bunjang
from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.ppomppu import parse_ppomppu
from collector.parsers.ruliweb import parse_ruliweb

FIXTURES = Path(__file__).parent / "fixtures"


def _read(rel: str) -> str:
    return (FIXTURES / rel).read_text(encoding="utf-8")


def _read_cp949(rel: str) -> str:
    """뽐뿌는 `charset=euc-kr`을 선언하지만 실제로는 cp949 전용 바이트를 담는다(docs/98 실측).
    선언대로 euc-kr로 열면 illegal multibyte sequence로 터진다. 디코딩은 호출자 책임 — 파서는 str만 받는다."""
    return (FIXTURES / rel).read_bytes().decode("cp949")


def test_bunjang_golden_first_item():
    deals = parse_bunjang(_read("bunjang/find_v2_iphone.json"))

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
    deals = parse_ruliweb(_read("ruliweb/list_normal.html"))

    assert len(deals) == 28  # docs/98 실측: info_article_id 28건
    d = deals[0]
    assert d.post_id == "105373"
    assert "빙그레" in d.title
    assert "read/105373" in d.url
    assert d.reaction_score == 3
    assert d.headline_price == 49_560  # 제목 내 가격(BM-02 정규화)


def test_fmkorea_golden_rows():
    deals = parse_fmkorea(_read("fmkorea/list_normal.html"))

    assert len(deals) == 20  # docs/98 실측: hotdeal_info 20건
    d = deals[0]
    assert d.post_id == "10041875674"
    assert d.title == "더미식 국물요리 350g X 5개 골라담기"
    assert d.headline_price == 13_800  # hotdeal_info 가격+무료배송
    assert d.reaction_score == 0
    assert d.url == "https://www.fmkorea.com/10041875674"
    assert d.status == "ACTIVE"


def test_ppomppu_golden_rows():
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"))

    assert len(deals) == 21  # docs/98 실측: 뽐뿌게시판 딜 행 21건
    d = deals[0]
    assert d.site == "ppomppu"
    assert d.post_id == "717553"
    assert d.title == "[옥션]1+1 소가죽 남성 핀 버클 벨트(11,800원/무료)"
    assert d.headline_price == 11_800  # 제목 내 가격(BM-02 정규화)
    assert d.reaction_score == 3  # .baseList-rec "3 - 0" = 추천 - 비추천
    assert d.status == "ACTIVE"
    assert d.posted_at is None  # 목록 시각은 당일/이전 형식이 갈린다(docs/98, Q-23)


def test_ppomppu_url_is_canonical():
    """href의 page·divpage는 페이지네이션 잔여물 — 자연키 URL은 board+no만."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"))

    assert deals[0].url == "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=717553"
    assert all("divpage" not in d.url and "page=" not in d.url for d in deals)


def test_ppomppu_excludes_other_board_widgets():
    """목록 페이지엔 뽐뿌마켓(id=pmarket)·자유게시판(id=social) 위젯 행이 섞여 있고,
    이들도 tr.baseList.bbs_new1을 쓴다. 글번호가 없어 자연키를 못 만든다 — 반드시 제외."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"))

    assert all(d.post_id.isdigit() for d in deals)
    assert all("id=ppomppu" in d.url for d in deals)
    post_ids = [d.post_id for d in deals]
    assert len(post_ids) == len(set(post_ids))  # (site, post_id) 자연키 무결성


def test_ppomppu_missing_recommend_is_zero():
    """신규 글은 .baseList-rec 텍스트가 비어 있다."""
    deals = parse_ppomppu(_read_cp949("ppomppu/list_normal.html"))

    assert deals[1].post_id == "717718"
    assert deals[1].reaction_score == 0
