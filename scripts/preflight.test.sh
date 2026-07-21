#!/usr/bin/env bash
# scripts/preflight.sh 계약: 조용히 오작동할 설정을 배포 전에 잡는가. 각 케이스는 mktemp .env로 격리한다
# ($(fn) 서브셸 함정 회피 — 카운터가 부모로 돌아오게 함수는 직접 호출).
set -uo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pass=0
fail=0

check() { # desc, mode, expected_exit, envcontent
	local desc="$1" mode="$2" want="$3" content="$4"
	local dir got
	dir="$(mktemp -d)"
	printf '%s\n' "$content" >"$dir/.env"
	bash "$root/scripts/preflight.sh" "$mode" "$dir/.env" >/dev/null 2>&1
	got=$?
	rm -rf "$dir"
	if [ "$got" = "$want" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$desc"
		pass=$((pass + 1))
	else
		printf '  FAIL  exit=%s(want %s)  %s\n' "$got" "$want" "$desc"
		fail=$((fail + 1))
	fi
}

echo "── preflight 계약 ──"
check "dev + DB_PASSWORD → OK" dev 0 "DB_PASSWORD=x"
check "DB_PASSWORD 없음 → FAIL" dev 1 "DB_USER=x"
check "prod + web 인증 없음 → FAIL(SEC-02 노출)" prod 1 "DB_PASSWORD=x"
check "prod + web 인증 있음 → OK" prod 0 "$(printf 'DB_PASSWORD=x\nWEB_BASIC_AUTH_HTPASSWD=hogu:hash')"
check "텔레그램 켰는데 토큰 없음 → FAIL" dev 1 "$(printf 'DB_PASSWORD=x\nTELEGRAM_ENABLED=true')"
check "텔레그램 켜고 토큰+chat → OK" dev 0 "$(printf 'DB_PASSWORD=x\nTELEGRAM_ENABLED=true\nTELEGRAM_BOT_TOKEN=t\nTELEGRAM_CHAT_ID=555')"
check "잘못된 mode → FAIL" bogus 1 "DB_PASSWORD=x"

# 없는 .env 경로 → FAIL (스택을 못 띄운다). 종료코드를 $?로 보지 않고 명령을 직접 if로 검사(SC2181).
if bash "$root/scripts/preflight.sh" dev /no/such/.env >/dev/null 2>&1; then
	printf '  FAIL  없는 .env가 FAIL이 아니다\n'
	fail=$((fail + 1))
else
	printf '  PASS  exit=%s  없는 .env → FAIL\n' "$?"
	pass=$((pass + 1))
fi

echo ""
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS ($pass)"
else
	echo "FAILED ($fail)" >&2
	exit 1
fi
