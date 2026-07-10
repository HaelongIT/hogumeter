#!/usr/bin/env bash
# `check-env-example.sh`의 계약 테스트.  실행: bash scripts/check-env-example.test.sh
#
# 차단 장치는 **무엇을 통과시켜야 하는가**를 먼저·더 많이 시험한다. 오차단은 CI를 빨갛게 만들어
# 개발을 마비시키고, 사람은 게이트를 끄는 쪽을 택한다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-env-example.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

check() { # expected_exit  label  compose_content  example_content
	printf '%s\n' "$3" >"$work/compose.yml"
	printf '%s\n' "$4" >"$work/env.example"
	set +e
	bash "$CHECK" "$work/compose.yml" "$work/env.example" >"$work/out" 2>&1
	local got=$?
	set -e
	if [ "$got" -eq "$1" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$2"
	else
		printf '  FAIL  expected=%s got=%s  %s\n' "$1" "$got" "$2"
		sed 's/^/        /' "$work/out"
		fail=1
	fi
}

echo "── 차단되어야 함 (exit 1) ──"
check 1 "문서화되지 않은 변수" \
	'services: { core: { environment: { X: "${UNDOCUMENTED_VAR}" } } }' \
	'DB_PASSWORD='
check 1 "기본값이 있어도 문서화는 필요하다 (조용히 기본값으로 돈다)" \
	'services: { core: { environment: { X: "${SILENT_DEFAULT:-ecs}" } } }' \
	'DB_PASSWORD='
check 1 "필수 표기(:?)도 예외가 아니다" \
	'services: { db: { environment: { P: "${REQUIRED_SECRET:?}" } } }' \
	'DB_PASSWORD='

echo "── 통과해야 함 (exit 0) — 오차단 방지 ──"
check 0 "전부 문서화됨" \
	'services: { core: { environment: { A: "${DB_PASSWORD:?}", B: "${CORE_PORT:-8080}" } } }' \
	'DB_PASSWORD=
CORE_PORT=8080'
check 0 ".env.example에만 있는 변수는 정상 (스크립트·미래 어댑터가 읽는다)" \
	'services: { core: { environment: { A: "${DB_PASSWORD:?}" } } }' \
	'DB_PASSWORD=
AWS_ACCESS_KEY_ID=
TELEGRAM_ALLOWED_CHAT_IDS='
check 0 "compose가 환경변수를 하나도 안 읽어도 실패가 아니다" \
	'services: { core: { image: postgres } }' \
	'DB_PASSWORD='
check 0 ".env.example의 주석은 변수가 아니다" \
	'services: { core: { environment: { A: "${DB_PASSWORD:?}" } } }' \
	'# TELEGRAM_BOT_TOKEN 은 발급 전이다
DB_PASSWORD='
check 0 '소문자·$$ 이스케이프는 환경변수 참조가 아니다' \
	'services: { core: { command: "echo $${literal} ${DB_PASSWORD}" } }' \
	'DB_PASSWORD='

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 docker-compose.yml ↔ .env.example 드리프트\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

echo

# **주석은 설정이 아니다.** compose의 `# - X=${COMMENTED:-1}`은 compose가 읽지 않는다 —
# 그걸 요구하면 운영자에게 **존재하지 않는 손잡이**를 문서화하라고 시키는 오차단이 된다.
# (2026-07-10: 정적 검사 게이트 셋이 같은 방식으로 주석에 속았다.)
echo "── 주석은 설정이 아니다 (오차단 방지) ──"
check 0 "compose의 주석 속 변수는 요구하지 않는다" \
	'services:
  core:
    environment:
      # - COMMENTED=${COMMENTED_ONLY:-x}
      - KNOWN=${KNOWN:-1}' \
	'KNOWN=1'

check 1 "주석 옆의 진짜 변수는 여전히 요구한다" \
	'services:
  core:
    environment:
      # 아래는 진짜다
      - REAL=${REAL_VAR:-x}' \
	'KNOWN=1'

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
