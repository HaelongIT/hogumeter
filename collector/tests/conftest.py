"""통합 테스트용 Postgres 컨테이너.

스키마는 **미러를 만들지 않고 core의 실 마이그레이션을 적용**한다. 미러는 반드시 드리프트하고,
드리프트한 미러는 GREEN인 채로 거짓말한다. core가 `raw_deal_post`를 바꾸면 이 테스트가 즉시 깨진다 —
그게 계약 검증이다. (Flyway 자체는 core 단독 소유. collector는 읽어 실행할 뿐 마이그레이션하지 않는다.)
"""

from pathlib import Path

import pytest

CORE_SCHEMA = (
    Path(__file__).resolve().parents[2] / "core/src/main/resources/db/migration/V1__init.sql"
)


@pytest.fixture(scope="session")
def postgres_url() -> str:
    """세션당 컨테이너 1개. 테스트마다 띄우면 루프가 못 견딘다."""
    postgres = pytest.importorskip("testcontainers.postgres")
    with postgres.PostgresContainer("postgres:16", driver=None) as container:
        yield container.get_connection_url()


@pytest.fixture(scope="session")
def schema_sql() -> str:
    assert CORE_SCHEMA.exists(), f"core 계약 스키마가 없다: {CORE_SCHEMA}"
    return CORE_SCHEMA.read_text(encoding="utf-8")


@pytest.fixture
def connection(postgres_url: str, schema_sql: str):
    """매 테스트마다 스키마를 새로 만든다(drop schema → 재생성). 테스트 간 격리."""
    import psycopg

    with psycopg.connect(postgres_url) as conn:
        with conn.cursor() as cursor:
            cursor.execute("drop schema public cascade; create schema public;")
            cursor.execute(schema_sql)
        conn.commit()
        yield conn
