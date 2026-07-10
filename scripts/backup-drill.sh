#!/usr/bin/env bash
# REL-04 백업→복원 왕복 리허설 — `backup.sh`와 `restore-drill.sh`를 한 번에 돌린다.
#
#   bash scripts/backup-drill.sh
#
# 왜 필요한가: `restore-drill.sh`는 **덤프가 이미 있어야** 돈다. 그래서 CI에서 한 번도 돌지 않았고,
# CLAUDE.md는 "복구 드릴은 전부 CI가 돌린다"고 적어 두고 있었다(2026-07-10 실측: 거짓).
# **검증되지 않은 백업은 백업이 아니고, 돌지 않는 드릴은 드릴이 아니다.**
#
# 흐름: 격리 스택 기동(postgres + core=Flyway) → 제품 1건 등록 → pg_dump → 일회용 컨테이너에 복원
#       → 스키마·flyway 이력·**행**까지 확인.
#
# 격리 규율: 전용 프로젝트 이름·전용 포트·전용 백업 디렉토리. 개발용 스택·`backups/`를 건드리지 않는다.

set -euo pipefail

PROJECT="hogumeter-backup-drill"
export DB_PASSWORD="${DB_PASSWORD:-drill-only-not-a-secret}"
export POSTGRES_PORT="${POSTGRES_PORT:-55433}"
export CORE_PORT="${CORE_PORT:-58081}"
export WEB_PORT="${WEB_PORT:-53001}"
export COLLECTOR_ALLOW_NETWORK=0
# 스케줄러를 끄지는 못하지만(환경변수는 주기만 바꾼다) 드릴은 딜을 만들지 않으므로 무해하다.
export CORE_PIPELINE_INTERVAL_MS=60000

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
work=$(mktemp -d)
CORE="http://127.0.0.1:${CORE_PORT}"

compose() { docker compose -p "$PROJECT" "$@"; }

cleanup() {
	echo "--- 정리 (전용 프로젝트·전용 백업 디렉토리만) ---"
	compose down -v --remove-orphans >/dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

fail() {
	echo "FAIL: $*" >&2
	compose logs --tail 30 core >&2 || true
	exit 1
}

echo "--- 1) 격리 스택 기동 (postgres + core: Flyway가 스키마를 만든다) ---"
cd "$root"
compose up -d --build core >/dev/null 2>&1 || fail "스택 기동 실패"

for _ in $(seq 60); do
	if curl -fsS -o /dev/null --max-time 5 "${CORE}/api/v1/health" 2>/dev/null; then
		ready=1
		break
	fi
	sleep 2
done
[ "${ready:-0}" = 1 ] || fail "core가 120초 안에 준비되지 않았다"

echo "--- 2) 복원을 증명할 행을 심는다 (빈 덤프는 아무것도 증명하지 못한다) ---"
payload="$work/product.json"
cat >"$payload" <<'JSON'
{"name":"복원 드릴 제품","category":"test","demandAxisMode":"GROUPED",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}}],
 "aliases":["복원드릴"]}
JSON
curl -fsS -X POST "${CORE}/api/v1/products" -H 'Content-Type: application/json' -d @"$payload" |
	grep -q '"productId"' || fail "제품 등록 실패"

echo "--- 3) backup.sh (전용 백업 디렉토리) ---"
PROJECT="$PROJECT" BACKUP_DIR="$work/backups" bash scripts/backup.sh || fail "backup.sh 실패"
dump=$(ls -1t "$work/backups"/*.sql.gz 2>/dev/null | head -1 || true)
[ -n "$dump" ] || fail "덤프가 만들어지지 않았다"

echo "--- 4) restore-drill.sh (일회용 컨테이너, 볼륨 없음) ---"
bash scripts/restore-drill.sh "$dump" || fail "복원 드릴 실패"

echo
echo "BACKUP DRILL PASS: 등록 -> pg_dump -> 격리 복원 -> 스키마·flyway 이력·행 확인"
