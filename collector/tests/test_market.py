"""중고 검색 → SiteSpec 순수 변환(USED-02)."""

from __future__ import annotations

from datetime import timedelta

import pytest

from collector.db.used_search_source import UsedSearchSpec
from collector.parsers.bunjang import parse_bunjang
from collector.scheduler.market import market_spec
from collector.scheduler.policy import SiteKind


def _search(id: int = 1, required=("아이폰17", "256"), platform="BUNJANG", poll_min=10) -> UsedSearchSpec:
    return UsedSearchSpec(
        id=id, platform=platform, required_keywords=list(required), poll_interval_min=poll_min
    )


def test_query_keywords_become_a_url_encoded_bunjang_search():
    spec = market_spec(_search(required=("아이폰17", "256")))

    assert spec.url.startswith("https://api.bunjang.co.kr/api/1/find_v2.json?q=")
    assert "order=date" in spec.url
    # 한글·공백은 인코딩된다 — 날것으로 들어가면 요청이 깨지거나 주입이 된다.
    assert "아이폰" not in spec.url
    assert " " not in spec.url
    assert "%EC" in spec.url  # 한글 UTF-8 인코딩 흔적


def test_it_is_a_marketplace_so_the_600s_floor_applies():
    """마켓은 게시판(60s)이 아니라 600s 하한이다 — `run_cycle`이 이 kind로 하한을 강제한다(SEC-08)."""
    spec = market_spec(_search())

    assert spec.kind is SiteKind.MARKETPLACE
    assert spec.parse is parse_bunjang
    assert spec.encoding == "utf-8"


def test_poll_interval_comes_from_the_search():
    assert market_spec(_search(poll_min=10)).interval == timedelta(minutes=10)


def test_each_search_gets_a_unique_name_so_they_do_not_share_polling_state():
    """두 검색이 같은 name이면 한쪽 상태가 다른 쪽을 덮어 하나만 폴링된다."""
    a = market_spec(_search(id=3))
    b = market_spec(_search(id=7))

    assert a.name != b.name
    assert a.name == "bunjang#3"
    assert b.name == "bunjang#7"


def test_unknown_platform_is_rejected_not_silently_parsed_as_a_board():
    """모르는 플랫폼을 조용히 게시판 파서로 넘기면 중고가 신품처럼 해석된다 — 마지막 분기는 '해석 못 함'."""
    with pytest.raises(ValueError, match="모르는 중고 플랫폼"):
        market_spec(_search(platform="JUNGGONARA"))
