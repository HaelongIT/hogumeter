"""통합 테스트용 Postgres 컨테이너.

스키마는 **미러를 만들지 않고 core의 실 마이그레이션을 적용**한다. 미러는 반드시 드리프트하고,
드리프트한 미러는 GREEN인 채로 거짓말한다. core가 `raw_deal_post`를 바꾸면 이 테스트가 즉시 깨진다 —
그게 계약 검증이다. (Flyway 자체는 core 단독 소유. collector는 읽어 실행할 뿐 마이그레이션하지 않는다.)

**전부, 버전 순서대로** 적용한다. V1만 적용하면 운영 DB(V1+V2+…)와 다른 스키마 위에서 GREEN이
나온다 — 미러를 안 만들겠다고 해놓고 "V1까지만의 미러"를 만드는 셈이다. 새 마이그레이션이 들어오면
자동으로 포함되고, 그게 `raw_deal_post`를 건드리면 여기서 깨진다.
"""

import re
from pathlib import Path

import pytest

MIGRATIONS = Path(__file__).resolve().parents[2] / "core/src/main/resources/db/migration"

# Flyway 명명 규약: V<version>__<description>.sql. 사전순이 아니라 **버전 숫자순**으로 정렬한다
# (V10이 V2보다 먼저 오면 안 된다).
_VERSION = re.compile(r"^V(\d+)__")


def _ordered_migrations() -> list[Path]:
    files = [p for p in MIGRATIONS.glob("V*__*.sql") if _VERSION.match(p.name)]
    return sorted(files, key=lambda p: int(_VERSION.match(p.name).group(1)))


@pytest.fixture(scope="session")
def postgres_url() -> str:
    """세션당 컨테이너 1개. 테스트마다 띄우면 루프가 못 견딘다."""
    postgres = pytest.importorskip("testcontainers.postgres")
    with postgres.PostgresContainer("postgres:16", driver=None) as container:
        yield container.get_connection_url()


@pytest.fixture(scope="session")
def schema_sql() -> str:
    migrations = _ordered_migrations()
    assert migrations, f"core 계약 마이그레이션이 없다: {MIGRATIONS}"
    return "\n".join(path.read_text(encoding="utf-8") for path in migrations)


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
