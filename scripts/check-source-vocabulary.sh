#!/usr/bin/env bash
# collector의 **파서(생산자)** 하나하나가 core에서 어떻게 해석되는지 선언됐는지 본다.
#
#   bash scripts/check-source-vocabulary.sh [root]
#
# 왜 필요한가(2026-07-22 실사고): `parse_bunjang`(중고)은 핫딜 파서와 **같은 `ParsedDeal`**을 내
# 같은 `raw_deal_post`에 적재된다. core의 적재는 소스를 안 가렸으므로 번개를 켜는 순간 중고 가격이
# 신품 기준가로 들어갈 참이었다. 파서는 있는데 레지스트리가 비어 있어서 아무도 몰랐다.
#
# 그 결함을 `NewProductSources`(core 허용집합)로 막았는데, 그 집합은 collector 파서 목록의 **거울**이다.
# 거울은 드리프트하고, 드리프트한 거울은 GREEN인 채로 거짓말한다. 그래서 게이트로 잠근다.
#
# 판정: `collector/src/collector/parsers/*.py`의 각 파서에 대해
#   - core 허용집합에 있다        -> 신품 게시판으로 해석된다. OK
#   - `scripts/used-sources.txt`에 선언돼 있다 -> 신품이 아니라고 **명시적으로** 선언됨. OK
#   - 둘 다 아니다                -> FAIL(새 파서를 추가하고 분류를 안 했다 = 조용히 버려지거나 오염된다)
#   - **둘 다에 있다**            -> FAIL(모순 — 어느 쪽이 진실인지 알 수 없다)

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
allowlist_src="$root/core/src/main/java/dev/hogumeter/core/domain/deal/NewProductSources.java"
parsers_dir="$root/collector/src/collector/parsers"
used_decl="$root/scripts/used-sources.txt"

for required in "$allowlist_src" "$used_decl"; do
	[ -f "$required" ] || {
		echo "FAIL: 필요한 파일이 없다: $required" >&2
		exit 1
	}
done
[ -d "$parsers_dir" ] || {
	echo "FAIL: 파서 디렉토리가 없다: $parsers_dir" >&2
	exit 1
}

# core 허용집합: `Set.of("a", "b", "c")` 에서 이름만. 전체 줄이 주석인 것은 뺀다(주석은 배선이 아니다).
mapfile -t accepted < <(
	grep -vE '^[[:space:]]*(//|\*|/\*)' "$allowlist_src" |
		grep -oE 'Set\.of\([^)]*\)' |
		grep -oE '"[^"]+"' | tr -d '"' | sort -u
)
[ "${#accepted[@]}" -gt 0 ] || {
	echo "FAIL: core 허용집합을 읽지 못했다(NewProductSources의 형식이 바뀌었나?)" >&2
	exit 1
}

# 중고·비신품으로 **선언된** 소스. `이름  <사유>` 형식, 주석·빈 줄 무시.
mapfile -t declared_used < <(grep -vE '^[[:space:]]*(#|$)' "$used_decl" | awk '{print $1}' | sort -u)

# 생산자 = 파서 모듈. `models.py`·`__init__.py`는 파서가 아니다.
mapfile -t parsers < <(
	find "$parsers_dir" -maxdepth 1 -name '*.py' -exec basename {} .py \; |
		grep -vxE '__init__|models' | sort -u
)
[ "${#parsers[@]}" -gt 0 ] || {
	echo "FAIL: 파서를 하나도 찾지 못했다: $parsers_dir" >&2
	exit 1
}

has() { # has <needle> <haystack...>
	local needle="$1"
	shift
	local x
	for x in "$@"; do [ "$x" = "$needle" ] && return 0; done
	return 1
}

bad=0
for parser in "${parsers[@]}"; do
	in_core=0 in_used=0
	has "$parser" ${accepted+"${accepted[@]}"} && in_core=1
	has "$parser" ${declared_used+"${declared_used[@]}"} && in_used=1

	if [ "$in_core" -eq 1 ] && [ "$in_used" -eq 1 ]; then
		echo "FAIL: '$parser'가 신품 허용집합과 중고 선언에 **둘 다** 있다 — 어느 쪽이 진실인가." >&2
		bad=$((bad + 1))
	elif [ "$in_core" -eq 0 ] && [ "$in_used" -eq 0 ]; then
		echo "FAIL: 파서 '$parser'를 core가 어떻게 해석하는지 아무도 선언하지 않았다." >&2
		echo "  신품 게시판이면 NewProductSources의 허용집합에 넣고(안 넣으면 그 딜은 조용히 버려진다)," >&2
		echo "  중고·비신품이면 scripts/used-sources.txt에 사유와 함께 적으라(안 적으면 기준가가 오염된다)." >&2
		bad=$((bad + 1))
	fi
done

# 허용집합에만 있고 파서가 없는 이름도 알린다 — 죽은 어휘이거나 파서가 지워진 것이다.
for site in "${accepted[@]}"; do
	if ! has "$site" "${parsers[@]}"; then
		echo "FAIL: core 허용집합의 '$site'에 대응하는 collector 파서가 없다 — 죽은 어휘인가?" >&2
		bad=$((bad + 1))
	fi
done

if [ "$bad" -eq 0 ]; then
	echo "SOURCE VOCABULARY OK: 파서 ${#parsers[@]}개 — 신품 ${#accepted[@]} / 중고선언 ${#declared_used[@]}"
else
	echo "SOURCE VOCABULARY FAILED: $bad" >&2
	exit 1
fi
