"""테스트용 **세 게시판 스펙** — 프로덕션 레지스트리(`hotdeal_boards()`)와 분리된 정본.

**왜 분리하나**: 실제로 폴링하는 곳은 robots 실측에 따라 바뀐다(2026-07-22 현재 뽐뿌 1사 —
루리웹·펨코는 `robots.txt`가 금지). 그렇다고 아래 계약들이 사라지는 건 아니다:

  - 세 파서가 낸 딜이 **실 DB 스키마를 통과**하는가(status CHECK 포함)
  - `SOLD_OUT`·`배송비미상` 표식이 **DB까지 도달**하는가
  - 루프의 멀티사이트 의미 — 한 사이트 실패가 다른 사이트를 안 죽인다, 영향받은 사이트만 알림
  - 사이트별 카운터가 `cycle` 이벤트에 실리는가(합산이 사이트별 편차를 가리지 않게)

이 계약들이 **폴링 대상에 따라 켜졌다 꺼졌다 하면 안 된다.** 루리웹·펨코를 되살릴 때 그 파서가
멀쩡한지 보증하는 것도 이 테스트들이다. 그래서 레지스트리에 기대지 않고 여기서 만든다.

프로덕션 레지스트리 자체의 계약(무엇을 폴링하는가)은 `test_fetcher.py`가 따로 단언한다.
"""

from __future__ import annotations

from datetime import timedelta

from collector.parsers.fmkorea import parse_fmkorea
from collector.parsers.ppomppu import parse_ppomppu
from collector.parsers.ruliweb import parse_ruliweb
from collector.scheduler.loop import SiteSpec
from collector.scheduler.policy import SiteKind

_EVERY = timedelta(seconds=60)

#: golden fixture 경로(사이트별). `tests/fixtures/` 기준 상대 경로.
GOLDEN_OF = {
    "ppomppu": "ppomppu/list_normal.html",
    "ruliweb": "ruliweb/list_normal.html",
    "fmkorea": "fmkorea/list_normal.html",
}


def all_board_specs() -> list[SiteSpec]:
    """핫딜 3사 스펙. 호출할 때마다 새 리스트를 만든다(호출자가 변형해도 서로 안 깨지게)."""
    return [
        SiteSpec(
            name="ppomppu",
            kind=SiteKind.BOARD,
            interval=_EVERY,
            url="https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu",
            encoding="cp949",  # 선언은 euc-kr이나 cp949 전용 바이트를 담는다(docs/98)
            parse=parse_ppomppu,
        ),
        SiteSpec(
            name="ruliweb",
            kind=SiteKind.BOARD,
            interval=_EVERY,
            url="https://bbs.ruliweb.com/market/board/1020?view=thumbnail&page=1",
            encoding="utf-8",
            parse=parse_ruliweb,
        ),
        SiteSpec(
            name="fmkorea",
            kind=SiteKind.BOARD,
            interval=_EVERY,
            url="https://www.fmkorea.com/hotdeal",
            encoding="utf-8",
            parse=parse_fmkorea,
        ),
    ]
