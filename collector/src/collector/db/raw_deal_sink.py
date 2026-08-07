"""`RawDealRecord[]` → `raw_deal_post` 업서트. IO 전용(순수 변환은 `pipeline/ingest.py`).

## 업서트 정책 (2026-07-09 확정, decision-log)

`(site, post_id)` 자연키로 충돌하면 **변화 가능한 필드를 전부 갱신**한다. 재수집에 행 수는 불변이고
(REL-01 멱등), 품절·가격변경·추천수 변화는 기존 행에 반영된다(BM-01 AC-2). insert-only면 품절을
영원히 모른다.

**`posted_at`만 예외** — 글의 발생 시각이라 불변이다(확정본 C-2 firstSeen). 다만 처음 수집 때
못 얻었으면(목록에 날짜가 없거나 파싱 실패) 나중에 채운다: `COALESCE(기존, 신규)`.

core의 `RawDealPostUpserter`는 이 의미를 못박아둔 **명세**이지 쓰기 주체가 아니다(프로덕션에서
아무도 호출하지 않는다). 쓰기는 collector 몫이고, 그쪽은 url·title·captured_at·status만 갱신하므로
가격·추천수 변화를 놓친다. 여기서는 그것까지 반영한다.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import psycopg
from psycopg.types.json import Jsonb

from ..pipeline.ingest import RawDealRecord

_UPSERT = """
insert into raw_deal_post
    (site, post_id, url, title, headline_price, posted_at, captured_at, reaction_score, status, raw, origin)
values
    (%(site)s, %(post_id)s, %(url)s, %(title)s, %(headline_price)s, %(posted_at)s,
     %(captured_at)s, %(reaction_score)s, %(status)s, %(raw)s, %(origin)s)
on conflict (site, post_id) do update set
    url            = excluded.url,
    title          = excluded.title,
    headline_price = excluded.headline_price,
    captured_at    = excluded.captured_at,
    reaction_score = excluded.reaction_score,
    status         = excluded.status,
    raw            = excluded.raw,
    -- 발생 시각은 불변(C-2). 처음에 못 얻었으면 그때 채운다.
    posted_at      = coalesce(raw_deal_post.posted_at, excluded.posted_at)
    -- origin은 갱신하지 않는다 — 최초 발견 경로(LIVE/BACKFILL)는 불변(REG-04, Q-87).
    -- deal_event.origin과 같은 정책(유입 1회 판정, C-4류).
"""


@dataclass(frozen=True)
class RawDealSink:
    """열린 커넥션을 받는다. 생명주기는 호출자(엔트리포인트)가 관리한다."""

    connection: psycopg.Connection

    def upsert_all(self, records: list[RawDealRecord]) -> int:
        """배치 업서트. 배치 안의 (site, post_id) 중복은 `to_raw_records`가 이미 접었다.

        BE-02(코드리뷰 20260806): 예외 시 `rollback()` 없이 그대로 던지면 이 커넥션은
        PostgreSQL의 "current transaction is aborted" 상태로 영구히 남는다 — 게시판·중고·
        폴링상태·별칭 싱크가 **같은 커넥션 하나를 공유**하므로, 한 번의 쓰기 실패가 그 뒤 모든
        쓰기·조회를 연쇄 실패시킨다. 실패를 흡수하지 않는다(재-raise) — 호출자(`__main__`)가
        여전히 실패를 세고 판단해야 한다.
        """
        if not records:
            return 0
        try:
            with self.connection.cursor() as cursor:
                cursor.executemany(_UPSERT, [_params(record) for record in records])
            self.connection.commit()
        except Exception:
            self.connection.rollback()
            raise
        return len(records)


def _params(record: RawDealRecord) -> dict:
    return {
        "site": record.site,
        "post_id": record.post_id,
        "url": record.url,
        "title": record.title,
        "headline_price": record.headline_price,
        "posted_at": record.posted_at,
        "captured_at": record.captured_at,
        "reaction_score": record.reaction_score,
        "status": record.status,
        "raw": Jsonb(record.raw),  # jsonb 컬럼 — 크롤링 원본 보관 전용
        "origin": record.origin,
    }


def connect_from_env() -> psycopg.Connection | None:
    """compose가 주는 이름 그대로 읽는다. `DB_HOST`가 없으면 DB 미설정으로 본다."""
    host = os.environ.get("DB_HOST")
    if not host:
        return None
    return psycopg.connect(
        host=host,
        port=int(os.environ.get("DB_PORT", "5432")),
        dbname=os.environ.get("DB_NAME", "hogumeter"),
        user=os.environ.get("DB_USER", "hogumeter"),
        password=os.environ.get("DB_PASSWORD", ""),
    )
