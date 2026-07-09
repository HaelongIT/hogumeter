#!/usr/bin/env bash
# REL-05 롤백 리허설 — `db/rollback/R*.sql`이 **실제로 도는지** 확인한다.
#
#   bash scripts/rollback-drill.sh
#
# 롤백 스크립트는 사고가 나야 처음 실행된다. 그때 문법 오류나 외래키 의존 순서가 드러나면
# 이미 늦다. 그래서 매 커밋 리허설한다(CI `rollback` 잡).
#
# 검증하는 것:
#   1) 전진: V1..Vn을 버전 순서대로 적용하면 스키마가 선다
#   2) 후진: Rn..R1을 **역순**으로 적용하면 public 스키마가 비워진다
#   3) 순서 강제: 역순을 어기면(R1 먼저) 외래키 의존으로 **실패해야 한다** — 조용히 통과하면
#      "롤백했다"고 착각한 채 반쯤 부서진 DB가 남는다
#   4) 재전진: 롤백 뒤 다시 V1..Vn이 적용된다(잔여물 없음 — 시퀀스·타입·인덱스)
#
# 격리 규율: 일회용 컨테이너·볼륨 없음(tmpfs). 운영/개발 postgres에 절대 닿지 않는다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
migrations="$root/core/src/main/resources/db/migration"
rollbacks="$root/core/src/main/resources/db/rollback"

container="hogumeter-rollback-drill-$$"
db=hogumeter
user=hogumeter

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

fail() {
	echo "FAIL: $*" >&2
	exit 1
}

# Flyway 명명 규약: V<n>__ / R<n>__. 사전순이 아니라 **버전 숫자순**(V10 > V2).
versions() {
	local dir=$1 prefix=$2
	find "$dir" -maxdepth 1 -name "${prefix}*__*.sql" -printf '%f\n' 2>/dev/null |
		sed "s/^${prefix}\([0-9]\+\)__.*/\1/" | sort -n
}

psql_run() { docker exec -i "$container" psql -U "$user" -d "$db" --quiet --set ON_ERROR_STOP=1 "$@"; }
query() { docker exec "$container" psql -U "$user" -d "$db" -tAc "$1" | tr -d '\r'; }
table_count() { query "select count(*) from information_schema.tables where table_schema='public'"; }

apply() { # apply <dir> <prefix> <version>
	local file
	file=$(find "$1" -maxdepth 1 -name "$2$3__*.sql" | head -1)
	[ -n "$file" ] || fail "$2$3 스크립트를 찾지 못했다"
	psql_run <"$file" >/dev/null
}

migrate_up() {
	for v in $(versions "$migrations" V); do apply "$migrations" V "$v"; done
}

reset_db() { psql_run -c "drop schema public cascade; create schema public;" >/dev/null; }

echo "--- 일회용 postgres 기동 (볼륨 없음) ---"
docker run -d --name "$container" \
	-e POSTGRES_DB="$db" -e POSTGRES_USER="$user" -e POSTGRES_PASSWORD=drill \
	--tmpfs /var/lib/postgresql/data:rw postgres:16 >/dev/null
for _ in $(seq 30); do
	docker exec "$container" pg_isready -U "$user" -d "$db" >/dev/null 2>&1 && ready=1 && break
	sleep 1
done
[ "${ready:-0}" = 1 ] || fail "postgres가 준비되지 않았다"

up_versions=$(versions "$migrations" V | tr '\n' ' ')
down_versions=$(versions "$rollbacks" R | sort -rn | tr '\n' ' ')
echo "--- 마이그레이션 [$up_versions] / 롤백 [$down_versions] ---"

# 모든 V에 짝이 되는 R이 있어야 한다. 없으면 그 버전은 되돌릴 수 없다.
for v in $(versions "$migrations" V); do
	find "$rollbacks" -maxdepth 1 -name "R${v}__*.sql" | grep -q . ||
		fail "V${v}의 롤백 스크립트(R${v}__*.sql)가 없다 — 되돌릴 수 없는 마이그레이션"
done

echo "--- 1) 전진: V를 버전 순서대로 적용 ---"
migrate_up
forward_tables=$(table_count)
[ "$forward_tables" -ge 12 ] || fail "전진 후 테이블이 ${forward_tables}개뿐이다"
query "select 1 from information_schema.tables where table_name='purchase'" | grep -q 1 ||
	fail "V2가 적용되지 않았다"
echo "    테이블 ${forward_tables}개"

echo "--- 2) 순서 강제: 역순을 어기면 실패해야 한다 (R1 먼저) ---"
# purchase가 variant를 참조하므로 R1의 `drop table variant`가 막혀야 한다.
if psql_run <"$(find "$rollbacks" -name 'R1__*.sql' | head -1)" >/dev/null 2>&1; then
	fail "R2 없이 R1이 통과했다 — 외래키 의존이 지켜지지 않는다(반쯤 부서진 DB를 못 잡는다)"
fi
echo "    막혔다(정상). 스키마를 되돌리고 계속한다"
reset_db
migrate_up

echo "--- 3) 후진: R을 역순으로 적용하면 스키마가 빈다 ---"
for v in $down_versions; do apply "$rollbacks" R "$v"; done
remaining=$(table_count)
[ "$remaining" = 0 ] || fail "롤백 후에도 테이블 ${remaining}개가 남았다: $(query "select string_agg(table_name, ', ') from information_schema.tables where table_schema='public'")"
echo "    테이블 0개"

echo "--- 4) 재전진: 잔여물(시퀀스·타입) 없이 다시 올라간다 ---"
migrate_up
[ "$(table_count)" = "$forward_tables" ] || fail "재전진 후 테이블 수가 다르다"

echo
echo "ROLLBACK DRILL PASS: V[$up_versions] -> R[$down_versions] -> V[$up_versions]"
