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

# fake <python값> <java값> <web값> — 빈 문자열이면 그 모듈이 상수를 아예 쓰지 않는다.
#
# **`mktemp`로 매번 새 디렉토리.** `$(fake …)`는 명령 치환 = 서브셸이라 카운터 증가가 부모로
# 돌아오지 않는다 — 카운터로 이름을 지으면 모든 케이스가 같은 디렉토리를 재사용한다(2026-07-10 실측).
fake() {
	local r
	r=$(mktemp -d "$work/rXXXXXX")
	mkdir -p "$r/collector/src/collector/pipeline" "$r/core/src/main/java/dev/hogumeter/core/domain/deal" \
		"$r/web/src/review"
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
	if [ -n "$3" ]; then
		printf "const SHIPPING_UNKNOWN = '%s'\n" "$3" >"$r/web/src/review/present.ts"
	else
		printf '// 상수를 지웠다\n' >"$r/web/src/review/present.ts"
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
check 0 "세 리터럴이 같다" "$(fake '배송비미상' '배송비미상' '배송비미상')"
check 0 "정본을 바꿔도 사본이 둘 다 따라오면 통과한다" \
	"$(fake 'SHIPPING_UNKNOWN' 'SHIPPING_UNKNOWN' 'SHIPPING_UNKNOWN')"

echo "── 차단되어야 함 (exit 1) ──"
# 이게 이 게이트의 존재 이유: collector가 이름을 바꾸면 core는 조용히 0을 센다.
check 1 "collector가 표식 이름을 바꿨다 (core는 영원히 0을 센다)" "$(fake '배송비_미상' '배송비미상' '배송비_미상')"
check 1 "core 쪽 오타" "$(fake '배송비미상' '배송비미상 ' '배송비미상')"
# web이 어긋나면 화면이 "실제 결제가는 더 높습니다"를 조용히 멈춘다 — 가장 눈에 안 띄는 실패다.
check 1 "web 쪽만 어긋났다 (하한 경고가 조용히 사라진다)" "$(fake '배송비미상' '배송비미상' '배송비_미상')"
check 1 "정본에서 상수가 사라졌다" "$(fake '' '배송비미상' '배송비미상')"
check 1 "core 사본에서 상수가 사라졌다" "$(fake '배송비미상' '' '배송비미상')"
check 1 "web 사본에서 상수가 사라졌다" "$(fake '배송비미상' '배송비미상' '')"
check 1 "파일 자체가 없다" "$work/does-not-exist"

# **주석 처리된 옛 상수를 집으면 안 된다.** `head -1`은 파일 순서대로 첫 매치를 집는다 —
# 옛 값이 주석으로 남아 있으면 게이트가 그것을 사본으로 읽고 **멀쩡한 저장소를 차단**한다(오차단).
echo "── 주석은 코드가 아니다 (오차단 방지) ──"
r=$(fake '배송비미상' '배송비미상' '배송비미상')
printf '// 옛 값: public static final String SHIPPING_UNKNOWN = "배송비_미상";\n\tpublic static final String SHIPPING_UNKNOWN = "배송비미상";\n' \
	>"$r/core/src/main/java/dev/hogumeter/core/domain/deal/DealTags.java"
check 0 "core: 주석 처리된 옛 상수는 무시한다" "$r"

r=$(fake '배송비미상' '배송비미상' '배송비미상')
printf "// const SHIPPING_UNKNOWN = '배송비_미상'\nconst SHIPPING_UNKNOWN = '배송비미상'\n" \
	>"$r/web/src/review/present.ts"
check 0 "web: 주석 처리된 옛 상수는 무시한다" "$r"

r=$(fake '배송비미상' '배송비미상' '배송비미상')
printf '# SHIPPING_UNKNOWN = "배송비_미상"\nSHIPPING_UNKNOWN = "배송비미상"\n' \
	>"$r/collector/src/collector/pipeline/price.py"
check 0 "collector: 주석 처리된 옛 상수는 무시한다" "$r"

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
