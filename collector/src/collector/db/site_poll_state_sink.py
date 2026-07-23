"""사이트별 **마지막 성공 폴링 시각**을 `site_poll_state`에 적는다 (docs/03 3-2 관측시계).

왜 collector가 쓰는가: 이 사실을 아는 유일한 프로세스이기 때문이다. core는 이 값으로 신선도를
재는데(`ObservationClock`), 값이 없으면 벽시계로 대신하고 그러면 **수집이 멈춘 동안 딜이 늙는
것처럼 보여** 신호등이 "딜 없음"으로 거짓 강등된다.

**성공만 적는다.** 실패·차단 상태(연속 실패 수·`stopped`)는 여기 두지 않는다 — 그건 REL-03
폴링 커서(docs/91 Q-59)이고 D-3(차단 사이트 재개 경로) 확정 전에 영속하면 중지가 영구히 굳는다.
성공만 적는 테이블의 최악은 "값이 안 늘어난다"이고, 그건 곧 시계 정지 = 의도된 동작이다.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime

import psycopg

_UPSERT = """
insert into site_poll_state (site, last_successful_poll_at, updated_at)
values (%(site)s, %(at)s, %(at)s)
on conflict (site) do update
   set last_successful_poll_at = excluded.last_successful_poll_at,
       updated_at = excluded.updated_at
 where site_poll_state.last_successful_poll_at < excluded.last_successful_poll_at
"""


@dataclass(frozen=True)
class SitePollStateSink:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def record_successes(self, polled: dict[str, datetime]) -> int:
        """`{사이트: 성공 시각}`을 올린다. 시각이 뒤로 가는 갱신은 하지 않는다(`where` 절).

        되돌아가는 시계는 "그 사이 성공했다"는 사실을 지운다 — 여러 프로세스·재시작 순서와
        무관하게 단조 증가만 허용한다. 갱신된 행 수를 돌려준다(0도 정상: 새 성공이 없었다).
        """
        if not polled:
            return 0
        with self.connection.cursor() as cursor:
            cursor.executemany(
                _UPSERT, [{"site": site, "at": at} for site, at in sorted(polled.items())]
            )
            written = cursor.rowcount
        self.connection.commit()
        return max(written, 0)
