"""게시판 목록의 시각 문자열 → tz-aware datetime. 순수 함수(`now`는 주입).

3사가 하나의 규칙으로 수렴한다(docs/98 실측):

  - **시:분(:초)** = 당일 글. 뽐뿌 `21:10:11`, 펨코 `20:59`, 루리웹 `날짜 18:10`
  - **날짜**       = 그 날짜의 글, 시각 미상. 뽐뿌 `26/07/03`, 루리웹 `날짜 2026.07.03`

날짜만인 경우 **23:59 KST**로 둔다(`docs/91` Q-23 잠정값). 시각을 지어내지 않는다는 표시이자,
그날 안에 있었다는 사실만 보존한다.

왜 중요한가: core는 `firstSeen = postedAt ?? capturedAt`로 채운다(`IngestDealsUseCase`). postedAt이
없으면 3일 전 글도 "방금 발생한 딜"이 되어 기간 P 필터·딜 주기(CAD)·성적표 산입이 전부 어긋난다.

KST는 **고정 오프셋**이다 — 한국은 서머타임이 없어 `zoneinfo`/tzdata가 필요 없다.
"""

from __future__ import annotations

import re
from datetime import datetime, timedelta, timezone

KST = timezone(timedelta(hours=9))

# 시각만 있는 값이 **멀리** 미래일 수 있는 유일한 경우는 자정 경계다(00:05에 본 "23:50" = 어제 글).
# 반대로 우리 시계가 몇 분 뒤처지면 오늘 글이 살짝 미래로 보인다 — 그걸 하루 되돌리면 24시간 오차다.
# 그래서 "먼 미래"일 때만 어제로 본다.
_ROLLBACK_THRESHOLD = timedelta(hours=12)

# 루리웹은 스크린리더용 라벨("날짜 ")을 앞에 붙인다.
_LABEL = re.compile(r"^\s*날짜\s*")
_TIME = re.compile(r"^(\d{1,2}):(\d{2})(?::(\d{2}))?$")
_DATE = re.compile(r"^(\d{2}|\d{4})[/.](\d{1,2})[/.](\d{1,2})$")


def parse_board_time(text: str | None, now: datetime) -> datetime | None:
    """파싱 실패는 `None` — 호출자가 `capturedAt` 폴백을 쓴다(C-2)."""
    if not text:
        return None
    cleaned = _LABEL.sub("", text).strip()

    time_match = _TIME.match(cleaned)
    if time_match:
        return _today_at(time_match, now)

    date_match = _DATE.match(cleaned)
    if date_match:
        return _end_of(date_match)

    return None


def _today_at(match: re.Match, now: datetime) -> datetime | None:
    hour, minute = int(match.group(1)), int(match.group(2))
    second = int(match.group(3) or 0)
    today = now.astimezone(KST).date()
    try:
        posted = datetime(today.year, today.month, today.day, hour, minute, second, tzinfo=KST)
    except ValueError:
        return None  # 25:99 같은 쓰레기

    # 자정 직후 폴링이면 "오늘 23:50"이 하루 가까이 미래로 나온다 → 어제 글이다.
    # 반면 몇 분 앞선 값은 우리 시계가 뒤처진 것일 뿐이니 그대로 둔다(24시간 오차보다 낫다).
    return posted - timedelta(days=1) if posted - now > _ROLLBACK_THRESHOLD else posted


def _end_of(match: re.Match) -> datetime | None:
    year = int(match.group(1))
    if year < 100:
        year += 2000  # `26` → 2026
    try:
        # 시각 미상 → 23:59 KST (Q-23 잠정값). 그날 안에 있었다는 사실만 남긴다.
        return datetime(year, int(match.group(2)), int(match.group(3)), 23, 59, tzinfo=KST)
    except ValueError:
        return None  # 13/45/99
