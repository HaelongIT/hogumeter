"""BM-01 AC-3 파서 golden — fixture HTML/JSON → ParsedDeal 스냅샷. 실 네트워크 호출 금지(문자열 입력)."""

from pathlib import Path

from collector.parsers.bunjang import parse_bunjang
from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.ruliweb import parse_ruliweb

FIXTURES = Path(__file__).parent / "fixtures"


def _read(rel: str) -> str:
    return (FIXTURES / rel).read_text(encoding="utf-8")


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
