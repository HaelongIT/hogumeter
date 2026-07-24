"""사이트별 폴링 커서를 `site_poll_state`에 영속한다 (docs/03 3-2 관측시계 + REL-03 Q-59).

**마지막 성공 폴링 시각**은 core의 관측시계(`ObservationClock`)가 신선도 기산점으로 읽는다 —
값이 없으면 벽시계로 대신하고, 그러면 수집이 멈춘 동안 딜이 늙는 것처럼 보여 신호등이 "딜 없음"으로
거짓 강등된다.

**연속 실패 수·`next_attempt_at`·`stopped`**(V15, Q-59/D-3)는 core가 안 읽는다 — 오직 collector
자신이 재시작 후 커서를 복원하기 위해서다. D-3이 "설정/DB 값 수동 수정"으로 확정됐으므로,
차단된 사이트의 재개는 **운영자가 이 테이블의 행을 직접 UPDATE하는 것**이다(별도 명령·API 없음):

    UPDATE site_poll_state
       SET stopped = false, next_attempt_at = null, consecutive_failures = 0
     WHERE site = '<사이트>';

collector는 다음 사이클(또는 재시작 시 `load_states`)에서 그 값을 그대로 읽어 반영할 뿐이다.
"""

from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass

import psycopg

from ..scheduler.policy import SiteState

_PERSIST = """
insert into site_poll_state
    (site, last_successful_poll_at, consecutive_failures, next_attempt_at, stopped, updated_at)
values
    (%(site)s, %(last_successful_poll)s, %(consecutive_failures)s, %(next_attempt_at)s,
     %(stopped)s, %(now)s)
on conflict (site) do update
   set last_successful_poll_at = case
         when excluded.last_successful_poll_at is not null
              and (site_poll_state.last_successful_poll_at is null
                   or site_poll_state.last_successful_poll_at < excluded.last_successful_poll_at)
         then excluded.last_successful_poll_at
         else site_poll_state.last_successful_poll_at
       end,
       consecutive_failures = excluded.consecutive_failures,
       next_attempt_at = excluded.next_attempt_at,
       stopped = excluded.stopped,
       updated_at = excluded.updated_at
"""

_LOAD = """
select site, last_successful_poll_at, consecutive_failures, next_attempt_at, stopped
  from site_poll_state
"""


@dataclass(frozen=True)
class SitePollStateSink:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def persist_states(self, states: Mapping[str, SiteState], now: datetime) -> int:
        """이번 사이클의 사이트 커서 전체를 영속한다. `last_successful_poll_at`만 단조 증가

        (되돌아가는 시계는 "그 사이 성공했다"는 사실을 지운다 — 여러 프로세스·재시작 순서와 무관하게
        단조 증가만 허용). 나머지(연속 실패·다음 시도·중지)는 현재 인메모리 상태를 그대로 덮는다.

        **재개(D-3)는 재시작을 전제한다 — 실행 중 라이브 리로드가 아니다.** 운영자가 DB 행을 고쳐도
        지금 도는 프로세스는 그 사실을 모른다(커서는 기동 시 `load_states`로만 읽는다). 그 상태로
        다음 사이클이 이 함수를 부르면 인메모리의 옛 `stopped=True`를 그대로 다시 써서 방금 고친
        값을 되돌려 놓는다 — 그래서 재개 절차는 "DB 행 수정 → 컨테이너 재시작"이다(Q-59/D-3).
        """
        if not states:
            return 0
        rows = [
            {
                "site": s.site,
                "last_successful_poll": s.last_successful_poll,
                "consecutive_failures": s.consecutive_failures,
                "next_attempt_at": s.next_attempt_at,
                "stopped": s.stopped,
                "now": now,
            }
            for s in sorted(states.values(), key=lambda s: s.site)
        ]
        with self.connection.cursor() as cursor:
            cursor.executemany(_PERSIST, rows)
            written = cursor.rowcount
        self.connection.commit()
        return max(written, 0)

    def load_states(self) -> dict[str, SiteState]:
        """기동 시 영속된 커서를 불러온다. 재개(D-3)는 운영자가 DB 행을 직접 고치는 것이고,

        이 함수는 그렇게 갱신된 값을 그대로 읽어 반영할 뿐이다 — 별도 "재개 명령"이 없다.
        """
        with self.connection.cursor() as cursor:
            cursor.execute(_LOAD)
            rows = cursor.fetchall()
        return {
            site: SiteState(
                site=site,
                last_successful_poll=last_successful_poll_at,
                consecutive_failures=consecutive_failures,
                next_attempt_at=next_attempt_at,
                stopped=stopped,
            )
            for site, last_successful_poll_at, consecutive_failures, next_attempt_at, stopped in rows
        }
