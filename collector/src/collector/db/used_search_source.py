"""`used_search` 읽기 — collector가 **무엇을 검색할지 DB에서 받아오는** 경로(USED-01·02).

지금까지 collector는 DB에 쓰기만 했다(`raw_deal_sink`). 핫딜 게시판은 폴링 대상이 코드에 고정돼
있어 그걸로 충분했지만, 중고는 **사용자가 등록한 검색**이 대상이라 읽어야 한다(docs/91 Q-72).

읽기만 한다 — 이 테이블의 쓰기 주체는 core의 등록 REST다. 여기서 갱신하지 않는다.
"""

from __future__ import annotations

from dataclasses import dataclass

import psycopg

_SELECT = """
select id, platform, required_keywords, poll_interval_min
  from used_search
 order by id
"""


@dataclass(frozen=True)
class UsedSearchSpec:
    """폴링 한 건의 명세. `exclude_keywords`는 **일부러 안 읽는다** — 아래 주석 참조."""

    id: int
    platform: str
    required_keywords: list[str]
    poll_interval_min: int

    @property
    def query(self) -> str:
        """플랫폼 검색어. 필수 키워드를 공백으로 이어 붙인다(AND는 플랫폼 검색이 해석한다)."""
        return " ".join(self.required_keywords)


@dataclass(frozen=True)
class UsedSearchSource:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def all_searches(self) -> list[UsedSearchSpec]:
        with self.connection.cursor() as cursor:
            cursor.execute(_SELECT)
            rows = cursor.fetchall()
        return [
            UsedSearchSpec(
                id=row[0],
                platform=row[1],
                required_keywords=list(row[2] or []),
                poll_interval_min=row[3],
            )
            for row in rows
        ]


# `exclude_keywords`를 여기서 적용하지 않는 이유:
# `used_listing_observation`은 "그 시각 목록에 무엇이 있었는가"의 **있는 그대로의 스냅샷**이다.
# collector가 걸러 넣으면 core는 걸러진 사실을 알 수 없고(제외가 보이지 않는다), 나중에 제외
# 키워드를 바꿔도 과거 관측을 다시 해석할 수 없다. 거르는 판단은 core가 읽을 때 한다 —
# "데이터 진실을 바꾸는 규칙은 시스템 고정"(CLAUDE.md 원칙 4)의 적용이다.
