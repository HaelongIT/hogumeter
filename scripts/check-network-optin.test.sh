#!/usr/bin/env bash
# `check-network-optin.sh`의 계약 테스트.  실행: bash scripts/check-network-optin.test.sh
#
# 일회용 트리에 스크립트를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(로컬 curl을 외부로 오인)은 사람이 게이트를 꺼 버리게 만든다. 통과 케이스를 먼저·더 많이 짠다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-network-optin.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake <스크립트 본문> [allowlist 줄] — 일회용 root를 만들고 경로를 찍는다.
#
# **`mktemp`로 매번 새 디렉토리를 만든다.** 카운터(`case_no=$((case_no+1))`)를 쓰면 안 된다 —
# 이 함수는 `$(fake …)` 명령 치환 **서브셸**에서 돌아 대입이 부모로 돌아오지 않는다. 그러면 모든
# 케이스가 같은 디렉토리를 재사용하고, 앞 케이스가 남긴 allowlist가 뒤 케이스를 조용히 면제한다
# (실제로 그래서 차단 케이스 넷이 전부 통과했다 — 2026-07-10).
fake() {
	local r
	r=$(mktemp -d "$work/rXXXXXX")
	mkdir -p "$r/scripts" "$r/.claude/hooks" "$r/.githooks"
	printf '%s\n' "$1" >"$r/scripts/target.sh"
	if [ $# -ge 2 ]; then
		printf '%s\n' "$2" >"$r/scripts/network-optin-allowlist.txt"
	fi
	printf '%s' "$r"
}

check() { # expected_exit  label  root
	set +e
	bash "$CHECK" "$3" >"$work/out" 2>&1
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

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"
check 0 "네트워크 명령이 아예 없다" "$(fake 'echo hello')"
check 0 "로컬 루프백만 친다 (스모크)" "$(fake 'curl -fsS http://127.0.0.1:53000/api/v1/health')"
check 0 "localhost도 외부가 아니다" "$(fake 'wget http://localhost:9000/minio/health')"
check 0 "example.invalid는 테스트용 예약 도메인이다" "$(fake 'curl https://example.invalid/x')"
check 0 "변수로 조립한 URL은 리터럴이 아니다 (게이트는 필요조건일 뿐)" \
	"$(fake 'curl -fsS "$BASE_URL/robots.txt"')"
check 0 "외부 URL이지만 opt-in을 스스로 본다" \
	"$(fake '[ "${ALLOW_REAL_ROBOTS:-0}" = 1 ] || exit 0
curl -fsS https://www.ppomppu.co.kr/robots.txt')"
check 0 "주석 속 외부 URL은 실행이 아니다" \
	"$(fake '# 예시: curl https://www.ppomppu.co.kr/robots.txt
echo done')"
check 0 "allowlist에 선언된 파일 (URL이 데이터다)" \
	"$(fake 'expect_block "curl https://api.telegram.org/bot123/sendMessage"' 'target.sh  URL은 데이터다')"

echo "── 차단되어야 함 (exit 1) ──"
# 이게 이 게이트의 존재 이유: PreToolUse 훅은 스크립트 **안**을 보지 못한다(Q-60).
check 1 "opt-in 없이 실 사이트를 긁는다" "$(fake 'curl -fsS https://www.ppomppu.co.kr/zboard/zboard.php')"
check 1 "opt-in 없이 외부 API를 호출한다" "$(fake 'curl -X POST https://api.telegram.org/bot/sendMessage')"
check 1 "wget도 마찬가지다" "$(fake 'wget https://api.bunjang.co.kr/api/1/find_v2.json')"
check 1 "aws가 실 엔드포인트를 친다" "$(fake 'aws s3 cp x.gz s3://b/ --endpoint-url https://s3.amazonaws.com')"
check 1 "스크립트 디렉토리가 없다" "$work/does-not-exist"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

# 면제가 실제로 쓰이는지 — 0건이면 allowlist가 죽은 것이다(낡은 면제는 다음 결함을 숨긴다).
if bash "$CHECK" | grep -qE 'opt-in 또는 선언 [1-9]'; then
	printf '  PASS  exit=0  면제·opt-in 항목이 실제로 집계된다\n'
else
	printf '  FAIL  allowlist가 읽히지 않는다\n'
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
