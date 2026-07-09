"""폴링 대상 레지스트리 — `docs/98` 실측을 코드로 옮긴 사실 표.

M1 = 핫딜 3사. **번개장터는 여기 없다** — 검색어별 URL(`find_v2.json?q=…`)이라
`UsedSearch`(M2) 모델이 선행한다. 파서는 이미 있으니 그때 편입하면 된다.

주기는 사이트 종류의 하한(BOARD 60s)을 그대로 쓴다. 하한은 `policy.effective_interval`이
코드로 강제하므로 여기서 낮춰도 소용없다(SEC-08 "설정으로 완화 불가").
"""

from __future__ import annotations

from datetime import timedelta

from ..parsers.fmkorea import parse_fmkorea
from ..parsers.ppomppu import parse_ppomppu
from ..parsers.ruliweb import parse_ruliweb
from .loop import SiteSpec
from .policy import SiteKind

_BOARD_INTERVAL = timedelta(seconds=60)  # PERF-05 게시판당 1req/min


def hotdeal_boards() -> list[SiteSpec]:
    """핫딜 3사. 호출할 때마다 새 리스트를 만든다(호출자가 실수로 변형해도 전역이 안 깨지게)."""
    return [
        SiteSpec(
            name="ppomppu",
            kind=SiteKind.BOARD,
            interval=_BOARD_INTERVAL,
            url="https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu",
            # 페이지는 charset=euc-kr을 선언하지만 실제로는 cp949 전용 바이트를 담는다(docs/98).
            encoding="cp949",
            parse=parse_ppomppu,
        ),
        SiteSpec(
            name="ruliweb",
            kind=SiteKind.BOARD,
            interval=_BOARD_INTERVAL,
            url="https://bbs.ruliweb.com/market/board/1020?view=thumbnail&page=1",
            encoding="utf-8",
            parse=parse_ruliweb,
        ),
        SiteSpec(
            name="fmkorea",
            kind=SiteKind.BOARD,
            interval=_BOARD_INTERVAL,
            url="https://www.fmkorea.com/hotdeal",
            encoding="utf-8",
            parse=parse_fmkorea,
        ),
    ]
