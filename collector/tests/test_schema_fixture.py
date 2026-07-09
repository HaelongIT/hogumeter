"""테스트 스키마가 **운영 스키마와 같은가**를 지킨다.

"미러를 만들지 않는다"는 규율은 V1만 적용하는 순간 반쯤 무너진다 — 그건 "V1까지만의 미러"다.
"""

import pytest

import conftest


def test_migrations_are_ordered_by_version_number_not_alphabetically(tmp_path, monkeypatch):
    """V10이 V2보다 먼저 적용되면 스키마가 조용히 어긋난다. 사전순은 그렇게 정렬한다."""
    for name in ("V1__init.sql", "V2__purchase.sql", "V10__later.sql", "R1__rollback.sql"):
        (tmp_path / name).write_text("", encoding="utf-8")
    monkeypatch.setattr(conftest, "MIGRATIONS", tmp_path)

    names = [path.name for path in conftest._ordered_migrations()]

    assert names == ["V1__init.sql", "V2__purchase.sql", "V10__later.sql"]  # R은 롤백이라 제외


@pytest.mark.integration
def test_the_test_container_gets_every_migration_not_just_the_first(connection):
    """V2의 `purchase`가 없으면 collector는 운영과 다른 스키마 위에서 GREEN을 낸다."""
    with connection.cursor() as cursor:
        cursor.execute(
            "select table_name from information_schema.tables where table_schema = 'public'"
        )
        tables = {row[0] for row in cursor.fetchall()}

    assert "raw_deal_post" in tables  # V1 — collector가 쓰는 계약 테이블
    assert "purchase" in tables  # V2 — 우리가 안 쓰지만, 적용되지 않으면 미러가 된 것이다
