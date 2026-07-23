#!/usr/bin/env bash
# **코드가 인용하는 Q-ID가 보드에 실제로 있는가.**
#
#   bash scripts/check-board-references.sh [root]
#
# 왜 필요한가(2026-07-23 실사고): `market.py`가 `_LISTINGS_PER_POLL = 100  # 잠정 — docs/91 Q-74`라고
# 적었는데 **Q-74는 보드에 만들어지지 않았다.** 코드는 근거를 인용하는 것처럼 보이고, 보드에는 그
# 질문이 없다. 잠정값의 위험("100이 작으면 잘린 매물이 판매완료 오알림이 된다")이 어디에도 없었다.
#
# 기존 게이트들은 **allowlist가 인용한 Q**만 검사한다 — 주석·문서의 인용은 아무도 안 봤다.
#
# 판정: 소스·스크립트·문서에 나타나는 `Q-<숫자>` 전부가 `docs/91-open-questions.md`에 **어딘가**
# 나타나야 한다. **제목만 보지 않는다** — 해소된 항목은 제목이 지워지고 다른 항목 본문에 "(Q-38
# 해소됨 … 여기서 제거)"로 남는다. 그런 인용을 차단하면 멀쩡한 근거 주석이 대량 오차단된다.
# 상태도 묻지 않는다: 해소된 Q를 "왜 이렇게 됐는지"의 근거로 인용하는 것은 정상이다.
#
# 한 방향만 본다: 보드에 있는데 아무도 인용하지 않는 Q는 정상이다(사람이 정할 것을 적어둔 것).
#
# **명시된 한계**: 게이트의 계약 테스트(`*.test.sh`)는 가짜 보드와 존재하지 않는 Q를 만들어 쓰므로
# 검사 대상에서 뺀다. 즉 테스트 파일 안의 Q 인용은 보호되지 않는다 — 테스트는 의도의 기록이 아니다.
#
# 면제 목록은 없다. 인용을 지우거나 항목을 만들거나 둘 중 하나다 — 그게 이 게이트의 전부다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
board="$root/docs/91-open-questions.md"

[ -f "$board" ] || {
	echo "FAIL: 보드가 없다: $board" >&2
	exit 1
}

# 보드가 아는 Q = 제목이든 본문이든 보드에 나타나는 것 전부(위 "제목만 보지 않는다" 참조).
mapfile -t defined < <(grep -oE 'Q-[0-9]+' "$board" | sort -u)
[ "${#defined[@]}" -gt 0 ] || {
	echo "FAIL: 보드에서 Q를 하나도 찾지 못했다(파일이 비었나?)" >&2
	exit 1
}

declare -A known=()
for qid in "${defined[@]}"; do
	known["$qid"]=1
done

# 인용을 찾을 곳. 보드 자신은 제외한다(본문의 상호 참조는 여기서 다루지 않는다).
scan=()
for path in "$root/core/src/main" "$root/collector/src" "$root/web/src" "$root/scripts" \
	"$root/.claude" "$root/CLAUDE.md" "$root/docs/98-field-notes.md"; do
	[ -e "$path" ] && scan+=("$path")
done
[ "${#scan[@]}" -gt 0 ] || {
	echo "FAIL: 검사할 경로를 하나도 찾지 못했다: $root" >&2
	exit 1
}

missing=0
checked=0
while IFS= read -r line; do
	[ -n "$line" ] || continue
	file="${line%%:*}"
	qid="${line##*:}"
	checked=$((checked + 1))
	if [ -z "${known[$qid]:-}" ]; then
		echo "FAIL: '$file'이 인용한 $qid가 docs/91에 없다. 항목을 만들거나 인용을 지워라." >&2
		missing=$((missing + 1))
	fi
done < <(
	# `*.test.sh`는 가짜 보드를 만드는 계약 테스트다(위 "명시된 한계") — 그 안의 Q는 데이터다.
	grep -rlE 'Q-[0-9]+' --include='*.java' --include='*.py' --include='*.ts' --include='*.tsx' \
		--include='*.sh' --include='*.sql' --include='*.md' --include='*.txt' --include='*.yml' \
		-r "${scan[@]}" 2>/dev/null |
		grep -v '\.test\.sh$' |
		sort |
		while IFS= read -r file; do
			grep -ohE 'Q-[0-9]+' "$file" | sort -u | while IFS= read -r qid; do
				echo "${file#"$root/"}:${qid}"
			done
		done
)

if [ "$missing" -gt 0 ]; then
	echo "BOARD REFERENCES FAILED: 없는 Q 인용 $missing 건 (인용 $checked 건 · 보드가 아는 Q ${#defined[@]}개)" >&2
	exit 1
fi

echo "BOARD REFERENCES OK: 인용 $checked 건이 모두 보드에 있다 (보드가 아는 Q ${#defined[@]}개)"
