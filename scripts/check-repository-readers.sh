#!/usr/bin/env bash
# "쓰기만 하는 테이블"을 **한 층 아래**에서 잡는다 — 호출자 0인 리포지토리 조회 메서드.
#
#   bash scripts/check-repository-readers.sh [root]
#
# 왜 필요한가: `check-table-wiring.sh`는 테이블 이름이 프로덕션 코드에 **나타나면** 통과시킨다.
# 엔티티가 있으면 그걸로 충분하므로, `product_axis`처럼 **쓰기만 하고 아무도 읽지 않는 테이블**을
# 못 잡는다(그 게이트의 명시된 한계). 실제로 오늘까지 셋을 손으로 찾았다:
#   `alert_policy`(읽기만) · `review_queue_item`(쓰기만) · `product_axis`(쓰기만).
#
# 판정: `*Repository.java`가 선언한 **커스텀 조회 메서드**를, 그 리포지토리 타입을 쓰는
# 프로덕션 파일 중 누군가 실제로 호출하는가. **테스트는 호출자가 아니다**(CLAUDE.md).
#
# ⚠️ 수신자 타입으로 스코프해야 한다: `findByProductId`는 세 리포지토리가 각자 선언하므로,
# 메서드 이름만 세면 다른 리포지토리의 호출자가 죽은 것을 가린다(2026-07-11 실측).
#
# 면제는 `repository-readers-allowlist.txt`에 **열린 Q-ID와 함께** 선언한다 — Q가 닫히면 면제도 죽는다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
persistence="$root/core/src/main/java/dev/hogumeter/core/adapter/persistence"
sources="$root/core/src/main/java"
allowlist="$root/scripts/repository-readers-allowlist.txt"
board="$root/docs/91-open-questions.md"

[ -d "$persistence" ] || {
	echo "FAIL: 리포지토리 디렉토리가 없다: $persistence" >&2
	exit 1
}

mapfile -t repos < <(find "$persistence" -maxdepth 1 -type f -name '*Repository.java' | sort)
[ "${#repos[@]}" -gt 0 ] || {
	echo "FAIL: 리포지토리를 하나도 찾지 못했다(경로가 바뀌었나?)" >&2
	exit 1
}

# 면제: "<Repository>.<method>  <Q-ID>  <이유>"
declare -A excuse=()
if [ -f "$allowlist" ]; then
	while read -r target qid _rest; do
		case "$target" in '' | '#'*) continue ;; esac
		excuse["$target"]="$qid"
	done <"$allowlist"
fi

open_question() { # 인용한 Q가 docs/91에 **열려 있는가**
	[ -f "$board" ] || return 1
	grep -qE "^## .*${1}\." "$board"
}

# 주석은 호출이 아니다. 전체 줄이 주석인 것만 걷는다.
_CODE_ONLY='^[[:space:]]*(//|\*|/\*)'

fail=0
alive=0
excused=0

for repo_file in "${repos[@]}"; do
	repo=$(basename "$repo_file" .java)
	# 인터페이스 본문이 선언한 커스텀 메서드(상속받은 save/findAll/count는 대상 아님)
	mapfile -t methods < <(
		grep -vE "$_CODE_ONLY" "$repo_file" |
			grep -oP '^\s*(?:List<[^>]+>|Optional<[^>]+>|long|int|boolean|[A-Z][A-Za-z]+)\s+\K[a-z][A-Za-z]*(?=\()' || true
	)
	[ "${#methods[@]}" -gt 0 ] || continue

	for method in "${methods[@]}"; do
		# **수신자 타입으로 스코프**한다 — 그 리포지토리를 쓰는 파일에서만 호출을 찾는다.
		#
		# `xargs`가 grep의 exit 1(매치 없음)을 123으로 올리고 `pipefail`이 그걸 대입에 실어
		# `set -e`가 조용히 죽인다(축적된 규칙). 루프로 센다 — 파이프라인 상태에 기대지 않는다.
		callers=0
		while IFS= read -r user; do
			[ -n "$user" ] || continue
			if grep -vE "$_CODE_ONLY" "$user" | grep -qP "\.\s*${method}\s*\("; then
				callers=$((callers + 1))
			fi
		done < <(
			grep -rl "\b${repo}\b" "$sources" --include='*.java' 2>/dev/null |
				grep -v "/${repo}\.java$" || true
		)
		key="${repo}.${method}"
		qid="${excuse[$key]:-}"

		if [ "$callers" -gt 0 ]; then
			if [ -n "$qid" ]; then
				echo "FAIL: 낡은 면제: '$key'는 이제 호출된다. allowlist에서 지워라." >&2
				echo "  낡은 면제는 다음 결함을 숨긴다." >&2
				fail=1
			else
				alive=$((alive + 1))
			fi
			continue
		fi

		if [ -z "$qid" ]; then
			echo "FAIL: 호출자 0인 조회 메서드: $key" >&2
			echo "  프로덕션 코드에서 아무도 부르지 않는다 — **테스트는 호출자가 아니다.**" >&2
			echo "  그 테이블은 쓰기만 하고 있을 가능성이 높다. 소비자를 만들거나," >&2
			echo "  scripts/repository-readers-allowlist.txt에 열린 Q-ID와 함께 선언하라." >&2
			fail=1
		elif ! open_question "$qid"; then
			echo "FAIL: 만료된 면제: '$key'가 인용한 $qid 가 docs/91에 열려 있지 않다." >&2
			echo "  막고 있던 것이 해소됐다면 이제 소비자를 만들 때다." >&2
			fail=1
		else
			excused=$((excused + 1))
		fi
	done
done

[ "$fail" -eq 0 ] || exit 1
echo "REPOSITORY READERS OK: 조회 메서드 $((alive + excused))개 (호출됨 ${alive} · 미사용 선언 ${excused})"
