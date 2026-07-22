#!/usr/bin/env bash
# `check-source-vocabulary.sh`의 계약 테스트.  실행: bash scripts/check-source-vocabulary.test.sh
#
# 일회용 트리를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다. 케이스 격리는 카운터가 아니라
# `mktemp`로 한다(`$(fn)`는 서브셸이라 카운터가 부모로 안 돌아온다 — 그래서 앞 케이스가 남긴
# 파일이 뒤 케이스를 조용히 면제한 적이 있다).
#
# 오차단은 사람이 게이트를 꺼 버리므로 **통과 케이스를 먼저·더 많이** 시험한다.

set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-source-vocabulary.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake_root <dir> <core Set.of 줄> <파서 이름들…은 뒤에, used-sources 내용은 $2 다음 인자>
fake_root() { # <dir> <set_line> <used_txt> <parser…>
	local r="$1" set_line="$2" used_txt="$3"
	shift 3
	mkdir -p "$r/core/src/main/java/dev/hogumeter/core/domain/deal" "$r/collector/src/collector/parsers" "$r/scripts"
	cat >"$r/core/src/main/java/dev/hogumeter/core/domain/deal/NewProductSources.java" <<JAVA
package dev.hogumeter.core.domain.deal;
public final class NewProductSources {
	$set_line
}
JAVA
	printf '%s\n' "$used_txt" >"$r/scripts/used-sources.txt"
	# 파서가 아닌 모듈도 항상 둔다 — 게이트가 이것들을 파서로 세면 안 된다.
	: >"$r/collector/src/collector/parsers/__init__.py"
	: >"$r/collector/src/collector/parsers/models.py"
	local p
	for p in "$@"; do : >"$r/collector/src/collector/parsers/$p.py"; done
}

check() { # <expected_exit> <label> <root>
	bash "$CHECK" "$3" >"$work/out" 2>&1
	local got=$?
	if [ "$got" -eq "$1" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$2"
	else
		printf '  FAIL  expected=%s got=%s  %s\n' "$1" "$got" "$2"
		sed 's/^/        /' "$work/out"
		fail=1
	fi
}

new_case() { mktemp -d "$work/rXXXXXX"; }

echo "── 통과해야 함 (exit 0) ──"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu", "ruliweb");' 'bunjang  중고' ppomppu ruliweb bunjang
check 0 "모든 파서가 분류돼 있다(신품 2 + 중고선언 1)" "$r"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu");' '# 주석만 있고 선언은 없다' ppomppu
check 0 "used-sources의 주석·빈 줄은 선언으로 세지 않는다(중고 파서도 없다)" "$r"

# 주석 속 이름은 배선이 아니다 — 이것이 배선으로 읽히면 게이트가 가장 조용한 방식으로 초록이 된다.
r=$(new_case)
fake_root "$r" "$(printf 'Set.of("ppomppu");\n\t// Set.of("junggonara");')" 'bunjang  중고' ppomppu bunjang
check 0 "주석 속 Set.of는 허용집합으로 세지 않는다" "$r"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu", "ruliweb", "fmkorea");' 'bunjang  중고 마켓(C2C)' ppomppu ruliweb fmkorea bunjang
check 0 "실제 저장소와 같은 구성" "$r"

echo "── 차단되어야 함 (exit 1) ──"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu");' 'bunjang  중고' ppomppu bunjang danggeun
check 1 "새 파서를 추가하고 분류를 안 했다 — 조용히 버려지거나 기준가를 오염시킨다" "$r"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu", "bunjang");' 'bunjang  중고' ppomppu bunjang
check 1 "한 파서가 신품 허용집합과 중고 선언에 둘 다 있다(모순)" "$r"

r=$(new_case)
fake_root "$r" 'Set.of("ppomppu", "ruliweb");' 'bunjang  중고' ppomppu bunjang
check 1 "허용집합에 있는데 대응 파서가 없다 — 죽은 어휘(파서가 지워졌나)" "$r"

r=$(new_case)
fake_root "$r" 'private static final String X = "ppomppu";' 'bunjang  중고' ppomppu bunjang
check 1 "허용집합을 못 읽으면 FAIL(형식 변경 감지 — 조용히 0개로 통과하지 않는다)" "$r"

echo ""
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS"
else
	echo "SOME FAILED" >&2
	exit 1
fi
