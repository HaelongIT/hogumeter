#!/usr/bin/env bash
# REL-04 백업 — 실행 중인 compose postgres를 pg_dump로 떠서 gzip 보관하고 오래된 것을 지운다.
#
#   bash scripts/backup.sh                 # 기본 프로젝트(hogumeter)
#   PROJECT=other bash scripts/backup.sh   # 다른 compose 프로젝트
#
# cron 예(일 1회 03:10):
#   10 3 * * *  cd /srv/hogumeter && bash scripts/backup.sh >> backups/backup.log 2>&1
#
# **읽기만 한다.** 이 스크립트는 DB를 변경하지 않고, 지우는 것은 보존 기간이 지난 *덤프 파일*뿐이다.
# 주 1회 오프사이트(S3) 사본은 아직 없다 — pre-deploy §A.

set -euo pipefail

PROJECT="${PROJECT:-hogumeter}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
DB_NAME="${DB_NAME:-hogumeter}"
DB_USER="${DB_USER:-hogumeter}"

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
out_dir="${BACKUP_DIR:-$root/backups}"
mkdir -p "$out_dir"

stamp=$(date -u +%Y%m%dT%H%M%SZ)
out="$out_dir/${DB_NAME}-${stamp}.sql.gz"

if ! docker compose -p "$PROJECT" ps --status running --services 2>/dev/null | grep -qx postgres; then
	echo "backup: '$PROJECT' 프로젝트의 postgres가 실행 중이 아닙니다." >&2
	exit 1
fi

echo "backup: pg_dump -> $out"
# -T: TTY 없이. 실패하면 부분 파일을 남기지 않는다(set -o pipefail + trap).
trap 'rm -f "$out"' ERR
docker compose -p "$PROJECT" exec -T postgres \
	pg_dump --username="$DB_USER" --dbname="$DB_NAME" --clean --if-exists |
	gzip -9 >"$out"
trap - ERR

# 빈 덤프를 "성공"으로 남기지 않는다.
[ -s "$out" ] || {
	echo "backup: 덤프가 비었습니다." >&2
	rm -f "$out"
	exit 1
}
gzip -t "$out" || {
	echo "backup: gzip 무결성 검사 실패." >&2
	rm -f "$out"
	exit 1
}

echo "backup: $(du -h "$out" | cut -f1)  ($(zcat "$out" | wc -l) 줄)"

echo "backup: ${RETENTION_DAYS}일 지난 덤프 정리"
find "$out_dir" -name "${DB_NAME}-*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" -print -delete
