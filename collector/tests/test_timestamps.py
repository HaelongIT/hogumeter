"""게시판 목록 시각 파싱 — 3사가 하나의 규칙으로 수렴한다: 시:분(:초)이면 당일, 날짜면 그 날짜.

실측(docs/98):
  뽐뿌   `21:10:11` / `26/07/03`
  루리웹 `날짜 18:10` / `날짜 2026.07.03`
  펨코   `20:59`
"""

from datetime import datetime, timedelta, timezone

import pytest

from collector.pipeline.timestamps import KST, parse_board_time

# 2026-07-09 12:00 UTC = 2026-07-09 21:00 KST
NOW = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)


def _kst(y, m, d, hh, mm, ss=0) -> datetime:
    return datetime(y, m, d, hh, mm, ss, tzinfo=KST)


# ── 당일 시각 (시:분:초 / 시:분) ────────────────────────────────────────


@pytest.mark.parametrize(
    "text, expected",
    [
        ("21:10:11", _kst(2026, 7, 9, 21, 10, 11)),  # 뽐뿌
        ("20:59", _kst(2026, 7, 9, 20, 59)),  # 펨코
        ("날짜 18:10", _kst(2026, 7, 9, 18, 10)),  # 루리웹(스크린리더 라벨 접두)
        ("00:09", _kst(2026, 7, 9, 0, 9)),
    ],
)
def test_time_only_means_today_in_kst(text, expected):
    assert parse_board_time(text, NOW) == expected


def test_result_is_timezone_aware_and_comparable_to_utc():
    parsed = parse_board_time("21:10:11", NOW)

    assert parsed.tzinfo is not None
    assert parsed == datetime(2026, 7, 9, 12, 10, 11, tzinfo=timezone.utc)


def test_far_future_time_is_read_as_yesterday():
    """자정 직후 폴링이면 '23:50'은 어제 글이다(오늘로 읽으면 하루 가까이 미래)."""
    just_after_midnight = datetime(2026, 7, 9, 15, 5, tzinfo=timezone.utc)  # 7/10 00:05 KST

    assert parse_board_time("23:50", just_after_midnight) == _kst(2026, 7, 9, 23, 50)


def test_slightly_future_time_stays_today():
    """우리 시계가 몇 분 뒤처진 것뿐이다. 하루를 되돌리면 24시간 오차가 된다."""
    now = datetime(2026, 7, 9, 12, 0, tzinfo=timezone.utc)  # 21:00 KST

    assert parse_board_time("21:10:11", now) == _kst(2026, 7, 9, 21, 10, 11)


# ── 날짜만 (시각 미상) ──────────────────────────────────────────────────


@pytest.mark.parametrize(
    "text, expected",
    [
        ("26/07/03", _kst(2026, 7, 3, 23, 59)),  # 뽐뿌
        ("날짜 2026.07.03", _kst(2026, 7, 3, 23, 59)),  # 루리웹
        ("26.07.03", _kst(2026, 7, 3, 23, 59)),
    ],
)
def test_date_only_falls_back_to_end_of_day_kst(text, expected):
    """docs/91 Q-23 잠정값: 날짜만이면 23:59 KST. 시각을 지어내지 않는다는 표시."""
    assert parse_board_time(text, NOW) == expected


# ── 파싱 실패는 None (capturedAt 폴백을 부른다) ────────────────────────


@pytest.mark.parametrize("text", ["", "   ", "방금", "어제", "2026-07-03", "25:99", "13/45/99"])
def test_unparseable_text_is_none(text):
    assert parse_board_time(text, NOW) is None


def test_none_input_is_none():
    assert parse_board_time(None, NOW) is None


def test_two_digit_year_maps_to_this_century():
    assert parse_board_time("99/01/02", NOW) == _kst(2099, 1, 2, 23, 59)


def test_kst_is_a_fixed_offset():
    """한국은 서머타임이 없다. tzdata 의존 없이 고정 오프셋으로 충분하다."""
    assert KST.utcoffset(None) == timedelta(hours=9)
