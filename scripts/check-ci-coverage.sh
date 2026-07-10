#!/usr/bin/env bash
# "돌지 않는 드릴은 드릴이 아니다" — 드릴·계약 테스트가 **정말 CI에 걸려 있는가.**
#
#   bash scripts/check-ci-coverage.sh [ci.yml]
#
# 왜 필요한가: `restore-drill.sh`(REL-04)는 CI에 없었다. CLAUDE.md는 "전부 CI가 돌린다"고 적어
# 두고 있었다(2026-07-10 실측: 거짓). 손으로 한 번 대조해 고쳤지만, **다음 드릴을 만들 때
# 또 잊는다.** 사람의 기억을 장치로 바꾼다.
#
# 대상: `*-drill.sh`(리허설) · `*.test.sh`(게이트 계약 테스트) · `smoke.sh`(종단).
# 판정: ci.yml이 직접 부르거나, **ci.yml이 부르는 다른 스크립트가 부른다**(1단 닫힘).
#   예) `restore-drill.sh`는 ci.yml에 없지만 `backup-drill.sh`가 부르고, 그건 ci.yml에 있다.
#
# 여기 걸리지 않는 것(의도): `check-robots.sh`(사람이 실 사이트에 대고 돌린다) ·
# `backup.sh`·`offsite-upload.sh`(운영 명령. 드릴이 이들을 실행해 간접 검증한다).

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
ci="${1:-$root/.github/workflows/ci.yml}"

[ -f "$ci" ] || {
	echo "FAIL: CI 정의를 찾지 못했다: $ci" >&2
	exit 1
}

# 검사 대상. 경로가 아니라 파일명으로 본다 — ci.yml은 `bash scripts/x.sh`로 부른다.
mapfile -t targets < <(
	find "$root/scripts" "$root/.claude/hooks" "$root/.githooks" \
		-maxdepth 1 -type f \( -name '*-drill.sh' -o -name '*.test.sh' -o -name 'smoke.sh' \) \
		-printf '%p\n' 2>/dev/null | sort
)
[ "${#targets[@]}" -gt 0 ] || {
	echo "FAIL: 검사 대상 스크립트를 하나도 찾지 못했다(경로가 바뀌었나?)" >&2
	exit 1
}

# **주석은 실행이 아니다.** ci.yml은 스크립트 이름을 설명문에도 적는다(`restore-drill.sh`가 그랬다).
# 주석 줄을 걷어내고 본다 — 그러지 않으면 "주석에 이름이 있으니 CI가 돈다"고 결론짓는다.
ci_runnable=$(grep -vE '^[[:space:]]*#' "$ci")

in_ci() { printf '%s' "$ci_runnable" | grep -qF "$1"; }

# 1단 닫힘의 출발점 = ci.yml이 부르는 스크립트 중 **계약 테스트가 아닌 것**.
# 계약 테스트는 게이트를 *시험*할 뿐 CI를 대신해 *실행*하지 않는다. 게다가 스크립트 이름을
# 데이터로 언급하므로(`grep -v 'bash scripts/x.sh'`), 출발점에 넣으면 무엇이든 "호출됨"이 된다.
mapfile -t ci_scripts < <(
	printf '%s' "$ci_runnable" |
		grep -ohE '(scripts|\.claude/hooks|\.githooks)/[A-Za-z0-9._-]+' |
		grep -v '\.test\.sh$' | sort -u
)

# `caller`가 ci에 걸려 있고 그 안에서 `name`을 부르는가.
called_by_ci_script() {
	local name="$1" caller
	for caller in "${ci_scripts[@]}"; do
		[ -f "$root/$caller" ] || continue
		grep -qF "$name" "$root/$caller" && return 0
	done
	return 1
}

fail=0
direct=0
indirect=0
for target in "${targets[@]}"; do
	name=$(basename "$target")
	if in_ci "$name"; then
		direct=$((direct + 1))
	elif called_by_ci_script "$name"; then
		indirect=$((indirect + 1))
	else
		echo "FAIL: CI가 부르지 않는다: ${target#"$root/"}" >&2
		echo "  드릴·계약 테스트는 사고가 나야 처음 실행된다. ci.yml에 걸거나, CI가 부르는 다른 드릴이 부르게 하라." >&2
		fail=1
	fi
done

[ "$fail" -eq 0 ] || exit 1
echo "CI COVERAGE OK: 대상 ${#targets[@]}개 (직접 ${direct} · 다른 드릴을 통해 ${indirect})"
