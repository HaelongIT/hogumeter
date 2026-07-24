"""`AliasSource` — alias_dictionary 읽기(D-6 재료). core의 실 마이그레이션을 적용한 컨테이너에서 검증."""

import pytest

from collector.db.alias_source import AliasSource

pytestmark = pytest.mark.integration


def _insert_product(connection, name: str) -> int:
    with connection.cursor() as cursor:
        cursor.execute(
            "insert into product (name, category, demand_axis_mode) values (%s, 'test', 'GROUPED') "
            "returning id",
            (name,),
        )
        (product_id,) = cursor.fetchone()
    connection.commit()
    return product_id


def _insert_alias(connection, product_id: int | None, alias: str) -> None:
    with connection.cursor() as cursor:
        cursor.execute("insert into alias_dictionary (product_id, alias) values (%s, %s)",
                        (product_id, alias))
    connection.commit()


def test_no_aliases_is_an_empty_list(connection):
    assert AliasSource(connection).all_aliases() == []


def test_returns_every_registered_alias(connection):
    pid = _insert_product(connection, "닌텐도 스위치2")
    _insert_alias(connection, pid, "스위치2")
    _insert_alias(connection, pid, "닌텐도스위치2")

    aliases = AliasSource(connection).all_aliases()

    assert sorted(aliases) == ["닌텐도스위치2", "스위치2"]


def test_global_alias_is_included(connection):
    """product_id가 null인 전역 별칭도 포함한다 — D-6은 "어느 제품인지"가 아니라

    "등록된 무언가에 걸리는가"만 필요하다.
    """
    _insert_alias(connection, None, "전역별칭")

    assert AliasSource(connection).all_aliases() == ["전역별칭"]
