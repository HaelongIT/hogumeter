"""중고 검색(`used_search`)을 폴링 가능한 `SiteSpec`으로 바꾸는 순수 변환(USED-02).

게시판은 고정 URL이라 레지스트리(`sites.py`)에 상수로 있지만, 중고는 **사용자가 등록한 검색어**가
대상이라 URL이 동적이다(`find_v2.json?q={검색어}`). 그 동적 명세를 `run_cycle`이 이해하는 `SiteSpec`으로
번역하면, robots·백오프·주기 하한(마켓 600s)·드리프트 감지를 게시판과 **같은 루프에서** 공짜로 얻는다.

IO 0. DB 읽기(`UsedSearchSource`)와 실 네트워크는 엔트리포인트가 지고, 여기는 변환만 한다.
"""

from __future__ import annotations

from datetime import timedelta
from urllib.parse import quote

from ..db.used_search_source import UsedSearchSpec
from ..parsers.bunjang import parse_bunjang
from .loop import SiteSpec
from .policy import SiteKind

#: 번개 비공식 검색 API(docs/98). 우리가 요청하는 유일한 호스트다 — 매물 상세는 안 친다(URL만 저장).
_BUNJANG_FIND = "https://api.bunjang.co.kr/api/1/find_v2.json"

#: 한 번에 훑을 매물 수. 저빈도·개인용이라 크게 잡을 이유가 없다(잠정 — 실 폴링으로 재조정, docs/91 Q-74).
_LISTINGS_PER_POLL = 100

_PLATFORM_PARSERS = {"BUNJANG": parse_bunjang}


def market_spec(search: UsedSearchSpec) -> SiteSpec:
    """한 중고 검색을 SiteSpec으로. `name`은 검색별로 유일해야 한다 — 안 그러면 두 검색이 같은
    폴링 상태(주기·백오프)를 공유해 하나만 폴링된다."""
    parse = _PLATFORM_PARSERS.get(search.platform)
    if parse is None:
        # 모르는 플랫폼을 조용히 게시판 파서로 넘기지 않는다 — 마지막 분기는 "해석 못 함"이어야 한다.
        raise ValueError(f"모르는 중고 플랫폼: {search.platform!r} (id={search.id})")

    # 검색어는 그대로 신뢰하지 않는다: 공백·한글이 그대로 URL에 들어가면 요청이 깨지거나 주입이 된다.
    query = quote(search.query, safe="")
    url = f"{_BUNJANG_FIND}?q={query}&order=date&n={_LISTINGS_PER_POLL}"

    return SiteSpec(
        # `#`는 search.id 구분자다. 카운터·로그·드리프트가 이 이름으로 검색을 가른다("bunjang#3").
        name=f"{search.platform.lower()}#{search.id}",
        kind=SiteKind.MARKETPLACE,  # 레이트 하한 600s — 설정으로도 못 낮춘다(SEC-08)
        interval=timedelta(minutes=search.poll_interval_min),
        url=url,
        encoding="utf-8",  # JSON 응답
        parse=parse,
    )
