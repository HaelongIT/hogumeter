"""`alias_dictionary` 읽기 — D-6의 "등록 제품 별칭이 걸리는가" 판단에 쓴다.

읽기만 한다 — 이 테이블의 쓰기 주체는 core의 등록 REST다. 여기서 갱신하지 않는다.
전역 별칭(product_id null)도 그대로 포함한다 — 이 seam은 "어느 제품인지"가 아니라
"등록된 무언가에 걸리는가"만 필요하다(collector/pipeline/detail_fetch.py).
"""

from __future__ import annotations

from dataclasses import dataclass

import psycopg

_SELECT = "select alias from alias_dictionary order by id"


@dataclass(frozen=True)
class AliasSource:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def all_aliases(self) -> list[str]:
        with self.connection.cursor() as cursor:
            cursor.execute(_SELECT)
            return [row[0] for row in cursor.fetchall()]
