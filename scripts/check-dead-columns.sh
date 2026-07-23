#!/usr/bin/env bash
# 죽은 컬럼 — DDL에 있으나 매핑도 네이티브 SQL도 없어 프로덕션 코드가 한 번도 닿지 않는 컬럼.
#
#   bash scripts/check-dead-columns.sh [root]
#
# 왜 필요한가: `deal_event.confidence`(매칭 신뢰도 자리)는 V1부터 **채우는 코드 0·읽는 코드 0**이었다.
# 2026-07-10 컬럼 소비처-0 **수동** 감사가 이걸 놓쳤다 — 유일한 언급이 `DealEventEntity`의 javadoc
# ("confidence는 미매핑")이라 "이름이 나타난다"에 걸렸기 때문이다(docs/91 Q-68). `check-table-wiring`은
# **테이블** 이름만 보므로 이 한 층 아래(테이블은 살아 있는데 컬럼이 죽은 경우)를 못 잡는다(그 게이트의 명시된 한계).
# 사람의 기억을 장치로 바꾼다 — 다음 죽은 컬럼을 CI가 막는다.
#
# 판정: 마이그레이션이 정의한 컬럼 이름(snake_case)이 **프로덕션 코드**(core/src/main/java · collector/src ·
# web/src, 테스트 제외)에 나타나는가 — snake_case(@Column(name=...)·네이티브 SQL) 또는 camelCase(암시적 JPA
# 매핑의 필드명) 어느 형태로든. 둘 다 안 나타나면 그 컬럼은 죽어 있다.
#
# 면제는 `scripts/dead-columns-allowlist.txt`에 선언한다. 사유는 둘 중 하나:
#   Q-<번호>    → docs/91에 **열려 있어야** 한다(해소되면 면제 만료 → 다시 묻는다). 잠정적으로 죽은 컬럼.
#   INTENTIONAL → 설계상 영원히 코드가 안 닿는 컬럼(base_price 역산금지, DB default now() 포렌식 타임스탬프).
# 게이트는 ① Q 인용이면 그 Q가 열려 있는지 ② 면제된 컬럼이 그새 배선되지 않았는지(낡은 면제는 다음 결함을 숨긴다)를 본다.
#
# 컬럼 추출은 **보수적**이다 — `^<공백><식별자> <알려진 타입>`만 컬럼으로 본다. 제약(check·constraint·
# unique·primary·foreign·references)·인덱스는 두 번째 토큰이 타입이 아니라 걸리지 않는다(오차단 회피).

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
migrations="$root/core/src/main/resources/db/migration"
allowlist="$root/scripts/dead-columns-allowlist.txt"
board="$root/docs/91-open-questions.md"

[ -d "$migrations" ] || {
	echo "FAIL: 마이그레이션 디렉토리가 없다: $migrations" >&2
	exit 1
}

# 컬럼 목록의 정본은 마이그레이션이다. 범위를 열어 둔다(`V*__*.sql` 전부).
# `table.column`으로 낸다 — 같은 컬럼명이 여러 테이블에 있어도(예: variant_id) 각각 판정한다.
_TYPES='bigint|bigserial|text|boolean|int|integer|numeric|timestamptz|smallint|jsonb|date|uuid'
mapfile -t columns < <(
	find "$migrations" -maxdepth 1 -type f -name 'V*__*.sql' -print0 |
		sort -z |
		xargs -0 -r awk -v types="$_TYPES" '
			/create table/ { t=$3; gsub(/[(]/,"",t); intable=1; next }
			intable && /^\);/ { intable=0; next }
			intable && match($0, "^[[:space:]]+([a-z_]+)[[:space:]]+("types")", m) { print t"."m[1] }
		' |
		sort -u
)
[ "${#columns[@]}" -gt 0 ] || {
	echo "FAIL: 마이그레이션에서 컬럼을 하나도 찾지 못했다(DDL 형식이 바뀌었나?)" >&2
	exit 1
}

# 프로덕션 코드만 본다. 테스트는 죽은 컬럼의 존재도 GREEN으로 잠근다.
sources=()
for dir in "$root/core/src/main/java" "$root/collector/src" "$root/web/src"; do
	[ -d "$dir" ] && sources+=("$dir")
done
[ "${#sources[@]}" -gt 0 ] || {
	echo "FAIL: 프로덕션 소스 디렉토리를 하나도 찾지 못했다: $root" >&2
	exit 1
}

# **주석은 배선이 아니다**(check-table-wiring과 같은 규율) — `confidence`가 정확히 javadoc에 걸렸다.
# 전체 줄이 주석인 것만 걷는다. 코드 옆 주석은 건드리지 않는다.
_CODE_ONLY='^[[:space:]]*(//|#|\*|/\*)'

# snake_case → camelCase (암시적 JPA 매핑의 필드명. price_first ↔ priceFirst).
camel() { echo "$1" | sed -E 's/_([a-z])/\U\1/g'; }

reached() { # 컬럼(snake 또는 camel)이 프로덕션 **코드**에 나타나는가
	local col="$1" cml file
	cml="$(camel "$col")"
	while IFS= read -r file; do
		[ -n "$file" ] || continue
		if grep -vE "$_CODE_ONLY" "$file" | grep -qP "\b(${col}|${cml})\b"; then
			return 0
		fi
	done < <(grep -rlP "\b(${col}|${cml})\b" "${sources[@]}" 2>/dev/null | grep -vE '\.test\.|/test/' || true)
	return 1
}

# 면제: "<table>.<column> <Q-ID|INTENTIONAL> <이유>". 주석·빈 줄 건너뛴다.
declare -A excuse=()
if [ -f "$allowlist" ]; then
	while read -r key qid _rest; do
		case "$key" in '' | '#'*) continue ;; esac
		excuse["$key"]="$qid"
	done <"$allowlist"
fi

q_open() { # 인용한 Q가 docs/91에 열려 있는가(해소된 Q를 인용한 면제는 만료)
	local qid="$1"
	[ -f "$board" ] || return 0 # 보드가 없으면 이 검사는 건너뛴다(다른 게이트가 잡는다)
	# 상태 표식 뒤에 날짜가 붙는다(`[부분해소 2026-07-22]`). 닫는 괄호를 바로 요구하면 그 형태를
	# **해소된 것으로 오독해** 멀쩡한 면제를 차단한다 — 오차단은 조용히 작업을 마비시킨다.
	# `해소`는 여전히 통과하지 않는다(대안이 `열림|부분해소`로 시작해야 한다).
	grep -qE "^#+ \[(열림|부분해소)[^]]*\] ${qid}\b" "$board"
}

dead=0
stale=0
for key in "${columns[@]}"; do
	col="${key#*.}"
	if reached "$col"; then
		# 배선돼 있다. 그런데 면제 목록에 있으면 낡은 면제다 — 지워야 한다.
		if [ -n "${excuse[$key]+x}" ]; then
			echo "FAIL: 낡은 면제: '$key'은 이제 코드가 닿는다. allowlist에서 지워라." >&2
			echo "  낡은 면제는 다음 죽은 컬럼을 숨긴다." >&2
			stale=$((stale + 1))
		fi
		continue
	fi
	# 코드가 안 닿는다 = 죽은 컬럼. 면제됐는가?
	if [ -n "${excuse[$key]+x}" ]; then
		qid="${excuse[$key]}"
		if [ "$qid" = "INTENTIONAL" ]; then
			continue # 설계상 영원히 안 닿는 컬럼
		fi
		if q_open "$qid"; then
			continue # 잠정적으로 죽음, 인용 Q가 열려 있음
		fi
		echo "FAIL: 면제 '$key'가 인용한 $qid가 docs/91에 열려 있지 않다(해소됨?). 면제를 지우거나 Q를 다시 열어라." >&2
		stale=$((stale + 1))
		continue
	fi
	echo "FAIL: 죽은 컬럼 '$key' — DDL에 있으나 프로덕션 코드가 닿지 않는다(매핑·네이티브 SQL 없음)." >&2
	echo "  살리거나(매핑/사용), 컬럼을 지우거나(마이그레이션), allowlist에 <Q-ID|INTENTIONAL>로 선언하라." >&2
	dead=$((dead + 1))
done

if [ "$dead" -eq 0 ] && [ "$stale" -eq 0 ]; then
	echo "DEAD COLUMNS OK: 컬럼 ${#columns[@]}개 (면제 ${#excuse[@]}개, 나머지 배선됨)"
else
	echo "DEAD COLUMNS FAILED: 죽은 컬럼 $dead · 낡은 면제 $stale" >&2
	exit 1
fi
