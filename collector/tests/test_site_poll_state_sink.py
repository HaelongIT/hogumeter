"""`SitePollStateSink`의 커서 영속화(REL-03 Q-59) + 재개(decisions-needed D-3) 계약.

core의 실 마이그레이션(V15까지)을 적용한 컨테이너에서 검증한다(`conftest.connection`).
핵심 계약 셋: ① 성공 시각은 단조 증가만, ② 실패해도 행은 생기되 성공 시각은 안 밀림,
③ 재개는 "DB 행 수동 UPDATE → 재시작(load_states)"으로 실제로 동작한다.
"""

from datetime import datetime, timedelta, timezone

import pytest

from collector.db.site_poll_state_sink import SitePollStateSink
from collector.scheduler.policy import SiteState

pytestmark = pytest.mark.integration

T0 = datetime(2026, 7, 24, 12, 0, tzinfo=timezone.utc)
T1 = T0 + timedelta(minutes=1)


def test_load_states_is_empty_before_anything_is_persisted(connection):
    sink = SitePollStateSink(connection)

    assert sink.load_states() == {}


def test_persist_and_load_round_trips_a_fresh_success(connection):
    sink = SitePollStateSink(connection)
    state = SiteState(site="ppomppu", last_successful_poll=T0, consecutive_failures=0,
                       next_attempt_at=T0 + timedelta(minutes=1), stopped=False)

    written = sink.persist_states({"ppomppu": state}, T0)

    assert written == 1
    assert sink.load_states() == {"ppomppu": state}


def test_persist_and_load_round_trips_a_stopped_site_with_no_success_ever(connection):
    """차단(BLOCKED)이 첫 시도부터 났다면 성공 시각이 아예 없다 — 가짜 시각을 지어내지 않는다."""
    sink = SitePollStateSink(connection)
    state = SiteState(site="fmkorea", last_successful_poll=None, consecutive_failures=0,
                       next_attempt_at=None, stopped=True)

    sink.persist_states({"fmkorea": state}, T0)

    assert sink.load_states() == {"fmkorea": state}


def test_last_successful_poll_only_advances(connection):
    """되돌아가는 시계는 "그 사이 성공했다"는 사실을 지운다 — 단조 증가만 허용."""
    sink = SitePollStateSink(connection)
    later = SiteState(site="ppomppu", last_successful_poll=T1, consecutive_failures=0,
                       next_attempt_at=None, stopped=False)
    earlier = SiteState(site="ppomppu", last_successful_poll=T0, consecutive_failures=0,
                         next_attempt_at=None, stopped=False)

    sink.persist_states({"ppomppu": later}, T1)
    sink.persist_states({"ppomppu": earlier}, T0)

    assert sink.load_states()["ppomppu"].last_successful_poll == T1


def test_a_later_failure_does_not_erase_an_earlier_success(connection):
    """성공(T0) 이후 실패 사이클(성공 시각 없음)이 와도 T0가 지워지면 안 된다 — 그러면 core의

    관측시계가 "이 사이트를 본 적이 있다"는 사실을 잃는다. `excluded.last_successful_poll_at`이
    null인 갱신은 기존 값을 그대로 둬야 한다(persist_states의 CASE 절이 이걸 지킨다).
    """
    sink = SitePollStateSink(connection)
    success = SiteState(site="ppomppu", last_successful_poll=T0, consecutive_failures=0,
                         next_attempt_at=T1, stopped=False)
    failure = SiteState(site="ppomppu", last_successful_poll=None, consecutive_failures=1,
                         next_attempt_at=T1 + timedelta(minutes=1), stopped=False)

    sink.persist_states({"ppomppu": success}, T0)
    sink.persist_states({"ppomppu": failure}, T1)

    loaded = sink.load_states()["ppomppu"]
    assert loaded.last_successful_poll == T0  # 안 지워짐
    assert loaded.consecutive_failures == 1  # 나머지 필드는 최신값으로 덮임
    assert loaded.next_attempt_at == T1 + timedelta(minutes=1)


def test_persisting_nothing_writes_nothing(connection):
    sink = SitePollStateSink(connection)

    assert sink.persist_states({}, T0) == 0


def test_manual_db_edit_resumes_a_stopped_site_after_restart(connection):
    """decisions-needed D-3의 실제 계약: 재개는 운영자가 DB 행을 직접 고치고 재시작하는 것.

    ① 프로세스가 차단(BLOCKED)을 겪고 stopped=True를 영속한다(사이클 종료).
    ② "재시작" = 새 `SitePollStateSink`가 `load_states()`로 그 값을 읽는다 — 여전히 stopped.
    ③ 운영자가 DB 행을 직접 UPDATE한다(사이트 seam 문서에 적힌 그 문장 그대로).
    ④ 다시 "재시작" — 이번엔 stopped=False로 복원돼 폴링이 재개될 수 있다.
    """
    sink = SitePollStateSink(connection)
    blocked = SiteState(site="ruliweb", last_successful_poll=T0, consecutive_failures=0,
                         next_attempt_at=None, stopped=True)
    sink.persist_states({"ruliweb": blocked}, T0)

    restarted = SitePollStateSink(connection).load_states()
    assert restarted["ruliweb"].stopped is True

    with connection.cursor() as cursor:
        cursor.execute(
            "update site_poll_state"
            "   set stopped = false, next_attempt_at = null, consecutive_failures = 0"
            " where site = %s",
            ("ruliweb",),
        )
    connection.commit()

    resumed = SitePollStateSink(connection).load_states()
    assert resumed["ruliweb"].stopped is False
    assert resumed["ruliweb"].next_attempt_at is None
    assert resumed["ruliweb"].last_successful_poll == T0  # 성공 이력 자체는 안 건드림
