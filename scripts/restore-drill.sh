#!/usr/bin/env bash
# REL-04 복원 리허설 — 덤프가 **실제로 복원되는지** 확인한다.
#
#   bash scripts/restore-drill.sh                 # 최신 덤프
#   bash scripts/restore-drill.sh backups/x.sql.gz
#
# 검증되지 않은 백업은 백업이 아니다. 이 스크립트는 **일회용 격리 컨테이너**에 덤프를 붓고,
# 스키마·행이 살아났는지 확인한 뒤 컨테이너를 버린다.
#
# 격리 규율: 운영/개발 postgres에 **절대 붓지 않는다.** 전용 컨테이너 이름·랜덤 포트를 쓰고
# 볼륨을 만들지 않는다(tmpfs). 실패해도 남는 것이 없다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
dump="${1:-$(ls -1t "$root"/backups/*.sql.gz 2>/dev/null | head -1 || true)}"
[ -n "$dump" ] && [ -f "$dump" ] || {
	echo "restore-drill: 덤프를 찾지 못했습니다. 먼저 bash scripts/backup.sh" >&2
	exit 1
}

container="hogumeter-restore-drill-$$"
db=hogumeter
user=hogumeter

cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "restore-drill: 일회용 postgres 기동 (볼륨 없음)"
docker run -d --name "$container" \
	-e POSTGRES_DB="$db" -e POSTGRES_USER="$user" -e POSTGRES_PASSWORD=drill \
	--tmpfs /var/lib/postgresql/data:rw postgres:16 >/dev/null

for _ in $(seq 30); do
	docker exec "$container" pg_isready -U "$user" -d "$db" >/dev/null 2>&1 && ready=1 && break
	sleep 1
done
[ "${ready:-0}" = 1 ] || {
	echo "restore-drill: postgres가 준비되지 않았습니다." >&2
	exit 1
}

echo "restore-drill: $dump 복원"
gzip -dc "$dump" | docker exec -i "$container" psql --username="$user" --dbname="$db" --quiet \
	--set ON_ERROR_STOP=1 >/dev/null

query() { docker exec "$container" psql -U "$user" -d "$db" -tAc "$1" | tr -d '\r'; }

tables=$(query "select count(*) from information_schema.tables where table_schema='public'")
[ "$tables" -ge 11 ] || {
	echo "restore-drill: 테이블이 ${tables}개뿐입니다(11개 이상 기대)." >&2
	exit 1
}

# Flyway 이력이 함께 복원돼야 core가 재기동 시 마이그레이션을 다시 돌리지 않는다.
query "select 1 from information_schema.tables where table_name='flyway_schema_history'" | grep -q 1 || {
	echo "restore-drill: flyway_schema_history가 없습니다 — core가 재마이그레이션을 시도한다." >&2
	exit 1
}

products=$(query "select count(*) from product")
echo "restore-drill: 테이블 ${tables}개, product ${products}행 복원 확인"

echo
echo "RESTORE DRILL PASS: $(basename "$dump")"
