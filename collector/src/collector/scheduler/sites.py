"""폴링 대상 레지스트리 — `docs/98` 실측을 코드로 옮긴 사실 표.

**M1 실 수집 대상은 뽐뿌 1사다**(2026-07-22 robots 실 대조로 확정). 루리웹·펨코는 각 사이트가
`robots.txt`로 **명시적으로 금지**해 뺐다 — 절대 원칙 5(기술적 차단 우회 금지):

  - **ruliweb**: `Disallow: /*view=` 가 우리 목록 URL(`?view=thumbnail`)을 금지한다.
    `view=` 없는 경로(`/market/board/1020`)는 허용되므로, 그 뷰의 golden fixture를 새로 받으면
    되살릴 수 있다(파서가 thumbnail 뷰 기준이라 구조 확인이 선행). → `docs/98` 2026-07-22
  - **fmkorea**: `/hotdeal` 자체가 `Disallow`. 재개 경로는 공식 API·제휴뿐이고, 그 전까지는
    M3 크롬 확장(사람이 보고 있는 화면을 읽는 반자동 폴백)이 설계된 길이다.

**여기서 뺐다고 게이트가 사라지는 게 아니다** — `HttpFetcher`가 요청 전에 `robots.allows`를 보고
막는다(2차 방어선). 레지스트리에서 빼는 이유는 따로다: 금지된 걸 스케줄에 두면 켤 때마다
SEC-08이 "차단 감지 → 자동 중지 + 관리 알림"을 내는데, **이미 아는 사실로 알림을 울릴 이유가 없다.**

**번개장터는 여기 없다** — 검색어별 URL(`find_v2.json?q=…`)이라 `UsedSearch`(M2) 모델이 선행한다.

주기는 사이트 종류의 하한(BOARD 60s)을 그대로 쓴다. 하한은 `policy.effective_interval`이
코드로 강제하므로 여기서 낮춰도 소용없다(SEC-08 "설정으로 완화 불가").
"""

from __future__ import annotations

from datetime import timedelta

# 루리웹·펨코 파서는 남아 있고 golden 테스트가 계속 지킨다(되살릴 때 여기 import만 되돌린다).
from ..parsers.bunjang import parse_bunjang
from ..parsers.fmkorea import parse_fmkorea
from ..parsers.ppomppu import parse_ppomppu
from ..parsers.ruliweb import parse_ruliweb
from .loop import SiteSpec
from .policy import SiteKind

_BOARD_INTERVAL = timedelta(seconds=60)  # PERF-05 게시판당 1req/min
_MARKET_INTERVAL = timedelta(minutes=10)  # SEC-08 마켓 하한 600s


def hotdeal_boards() -> list[SiteSpec]:
    """실 수집 대상. 호출할 때마다 새 리스트를 만든다(호출자가 실수로 변형해도 전역이 안 깨지게).

    지금은 **뽐뿌 1사**다 — 위 모듈 docstring의 robots 실측 참조. 파서(`parse_ruliweb`·
    `parse_fmkorea`)는 지우지 않는다: 되살릴 때 그대로 쓰고, 그때까지 golden 테스트가 계속 지킨다.
    """
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
    ]


def robots_check_targets() -> list[SiteSpec]:
    """**robots를 확인해야 하는 곳** — 지금 폴링하는 곳과 다르다.

    `hotdeal_boards()`를 좁히자(뽐뿌 1사) robots 확인 도구도 같이 좁아졌다. 그러면 정확히 **되살릴지
    판단해야 하는 사이트**(루리웹·펨코)를 다시는 못 본다 — 확인 도구가 "우리가 이미 켠 것만" 보는 셈이다.
    두 목록은 성격이 다르므로 따로 둔다(core의 `NewProductSources` 허용집합과 같은 이유).

    번개장터도 여기 든다. `parse_bunjang`·fixture·적재 경로는 다 있는데 **robots는 한 번도 확인된 적이
    없다** — 루리웹에서 정확히 그 가정("당연히 허용이겠지") 때문에 금지된 URL을 크롤링했다. 켜기 전에
    사람이 이 도구로 확인한다.
    """
    targets = hotdeal_boards()
    polled = {spec.name for spec in targets}
    candidates = [
        SiteSpec(
            name="ruliweb",
            kind=SiteKind.BOARD,
            interval=_BOARD_INTERVAL,
            # `view=` 없는 경로 — `Disallow: /*view=`에 걸리지 않는 후보 URL이다. 되살린다면 이 뷰다.
            url="https://bbs.ruliweb.com/market/board/1020",
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
        SiteSpec(
            name="bunjang",
            kind=SiteKind.MARKETPLACE,
            interval=_MARKET_INTERVAL,
            # 검색어는 `used_search`가 정한다(M2). robots 판정에는 경로만 있으면 되므로 대표 질의를 쓴다.
            url="https://api.bunjang.co.kr/api/1/find_v2.json?q=%EC%95%84%EC%9D%B4%ED%8F%B017&order=date",
            encoding="utf-8",
            parse=parse_bunjang,
        ),
    ]
    targets.extend(spec for spec in candidates if spec.name not in polled)
    return targets
