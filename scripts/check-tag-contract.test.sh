#!/usr/bin/env bash
# `check-tag-contract.sh`의 계약 테스트.  실행: bash scripts/check-tag-contract.test.sh
#
# 일회용 트리에 두 파일을 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-tag-contract.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0
case_no=0

# fake <python값> <java값> — 둘 중 하나가 빈 문자열이면 그 상수를 아예 쓰지 않는다.
fake() {
	case_no=$((case_no + 1))
	local r="$work/r$case_no"
	mkdir -p "$r/collector/src/collector/pipeline" "$r/core/src/main/java/dev/hogumeter/core/domain/deal"
	if [ -n "$1" ]; then
		printf 'SHIPPING_UNKNOWN = "%s"\n' "$1" >"$r/collector/src/collector/pipeline/price.py"
	else
		printf '# 상수를 지웠다\n' >"$r/collector/src/collector/pipeline/price.py"
	fi
	if [ -n "$2" ]; then
		printf '\tpublic static final String SHIPPING_UNKNOWN = "%s";\n' "$2" \
			>"$r/core/src/main/java/dev/hogumeter/core/domain/deal/DealTags.java"
	else
		printf '// 상수를 지웠다\n' >"$r/core/src/main/java/dev/hogumeter/core/domain/deal/DealTags.java"
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

echo "── 통과해야 함 (exit 0) ──"
check 0 "두 리터럴이 같다" "$(fake '배송비미상' '배송비미상')"
check 0 "정본을 바꿔도 사본이 따라오면 통과한다" "$(fake 'SHIPPING_UNKNOWN' 'SHIPPING_UNKNOWN')"

echo "── 차단되어야 함 (exit 1) ──"
# 이게 이 게이트의 존재 이유: collector가 이름을 바꾸면 core는 조용히 0을 센다.
check 1 "collector가 표식 이름을 바꿨다 (core는 영원히 0을 센다)" "$(fake '배송비_미상' '배송비미상')"
check 1 "core 쪽 오타" "$(fake '배송비미상' '배송비미상 ')"
check 1 "정본에서 상수가 사라졌다" "$(fake '' '배송비미상')"
check 1 "사본에서 상수가 사라졌다" "$(fake '배송비미상' '')"
check 1 "파일 자체가 없다" "$work/does-not-exist"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
