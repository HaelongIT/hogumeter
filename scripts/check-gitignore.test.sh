#!/usr/bin/env bash
# `check-gitignore.sh`의 계약 테스트.  실행: bash scripts/check-gitignore.test.sh
#
# 일회용 저장소에 `.gitignore`를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(`.env.example`이 무시됨)을 놓치면 운영자가 손잡이를 모른 채 배포한다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-gitignore.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0
case_no=0

GOOD_IGNORE='.env
.env.*
!.env.example
build/
node_modules/
.claude/settings.local.json
backups/
docker-compose.override.yml'

check() { # expected_exit  label  gitignore_내용  [추적할파일...]
	local expected="$1" label="$2" ignore="$3"
	shift 3
	case_no=$((case_no + 1))
	local repo="$work/r$case_no"
	mkdir -p "$repo"
	git -C "$repo" init -q
	git -C "$repo" config user.email drill@example.invalid
	git -C "$repo" config user.name drill
	git -C "$repo" config core.autocrlf false # 줄끝 경고는 이 테스트의 관심사가 아니다
	printf '%s\n' "$ignore" >"$repo/.gitignore"
	for tracked in "$@"; do
		mkdir -p "$repo/$(dirname "$tracked")"
		printf 'x\n' >"$repo/$tracked"
		git -C "$repo" add -f "$tracked"
	done
	git -C "$repo" add -f .gitignore
	git -C "$repo" commit -qm init

	set +e
	bash "$CHECK" "$repo" >"$work/out" 2>&1
	local got=$?
	set -e
	if [ "$got" -eq "$expected" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$label"
	else
		printf '  FAIL  expected=%s got=%s  %s\n' "$expected" "$got" "$label"
		sed 's/^/        /' "$work/out"
		fail=1
	fi
}

echo "── 차단되어야 함 (exit 1) ──"
check 1 "backups/ 한 줄이 지워졌다 (다음 add -A가 DB 덤프를 커밋한다)" \
	"$(printf '%s\n' "$GOOD_IGNORE" | grep -v '^backups/$')"
check 1 ".env가 무시되지 않는다 (토큰·API 키가 커밋된다)" \
	"$(printf '%s\n' "$GOOD_IGNORE" | grep -v '^\.env$' | grep -v '^\.env\.\*$')"
check 1 "docker-compose.override.yml이 무시되지 않는다 (스택이 조용히 달라진다)" \
	"$(printf '%s\n' "$GOOD_IGNORE" | grep -v '^docker-compose\.override\.yml$')"
check 1 ".env.example까지 무시한다 (오차단 — 운영자가 손잡이를 모른다)" \
	"$(printf '%s\n' "$GOOD_IGNORE" | grep -v '^!\.env\.example$')"
check 1 "이미 추적 중인 덤프가 있다 (.gitignore는 이걸 되돌리지 않는다)" \
	"$GOOD_IGNORE" "backups/hogumeter-20260101T000000Z.sql.gz"
check 1 "이미 추적 중인 .env가 있다" "$GOOD_IGNORE" ".env"

echo "── 통과해야 함 (exit 0) ──"
check 0 "정상 .gitignore" "$GOOD_IGNORE"
check 0 ".env.example은 추적돼도 위험 파일이 아니다" "$GOOD_IGNORE" ".env.example"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소의 .gitignore가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
