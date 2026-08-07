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
# 판정: 마이그레이션이 정의한 컬럼 이름(snake_case)이 **프로덕션 코드**에 나타나는가 — snake_case
# (@Column(name=...)·네이티브 SQL) 또는 camelCase(암시적 JPA 매핑의 필드명) 어느 형태로든.
#
# **생산자(collector/src)와 소비자(core/src/main/java·web/src)를 구별한다**(2026-08-07 리팩터,
# 코드리뷰 20260806 X-03/X-10 제안 반영). 예전엔 셋 중 아무 데나 나타나면 "배선됨"으로 뭉뚱그려서
# `raw_deal_post.reaction_score`가 정확히 이 함정에 빠졌다 — collector는 매 폴링마다 쓰는데 core는
# 한 번도 안 읽는 걸 게이트가 "OK"로 오판했다(docs/91 Q-89). 이제 셋은 두 그룹이다:
#   소비 = core/src/main/java · web/src  (core가 DB를 읽고, web은 core API를 통해 받는다)
#   생산 = collector/src                (DB에 쓰는 쪽)
# 소비되면 완전히 배선된 것. **생산만 되고 소비가 없으면** "반쪽 배선"(다음 참조) — 완전히 죽은 것과는
# 다른 카테고리다. 생산도 소비도 없으면 완전히 죽은 컬럼(기존과 동일).
#
# 면제는 `scripts/dead-columns-allowlist.txt`에 선언한다. 사유:
#   Q-<번호>      → docs/91에 **열려 있어야** 한다(해소되면 면제 만료 → 다시 묻는다). 완전히 죽었거나
#                   반쪽 배선인 컬럼 둘 다에 쓸 수 있다("아직 안 정해짐/안 만듦"이라는 뜻이라).
#   INTENTIONAL   → 설계상 영원히 완전히 안 닿는 컬럼(base_price 역산금지, DB default now() 포렌식
#                   타임스탬프). 완전히 죽은 컬럼 전용.
#   PRODUCER_ONLY → 설계상 영원히 생산만 하기로 확정(예: D-10 — reaction_score는 수집만 하고 노출은
#                   영구 포기). 반쪽 배선 전용, 만료 없음(INTENTIONAL의 반쪽 배선 버전).
# 게이트는 ① Q 인용이면 그 Q가 열려 있는지 ② 면제된 컬럼이 그새(완전히) 배선되지 않았는지(낡은
# 면제는 다음 결함을 숨긴다)를 본다.
#
# **알려진 한계(고치지 않음, 범위 밖)**: 컬럼 이름은 테이블 구분 없이 통짜로 매칭한다(`col` 하나만
# 보고 `table.col`은 안 본다) — 같은 짧은 이름(`raw` 등)이 다른 테이블에도 있으면 한쪽의 진짜 배선이
# 다른 쪽을 "소비됨"으로 오판시킬 수 있다(교차 테이블 충돌, 2026-08-07 실측: `used_listing_observation.raw`
# 가 `raw_deal_post.raw`의 네이티브 SQL 읽기와 충돌해 우연히 "소비됨"으로 나온다 — 지금은 결과적으로
# 안전한 방향의 오차단(과소 검출)이라 급하지 않지만, 테이블 인지형 매칭 없이는 못 고친다). 별도 발견.
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
# 소비자(core가 DB를 읽고 web은 core API로 받는다) — 이쪽이 비면 이 게이트 자체가 무의미하다.
consumer_sources=()
for dir in "$root/core/src/main/java" "$root/web/src"; do
	[ -d "$dir" ] && consumer_sources+=("$dir")
done
[ "${#consumer_sources[@]}" -gt 0 ] || {
	echo "FAIL: 프로덕션 소스 디렉토리를 하나도 찾지 못했다: $root" >&2
	exit 1
}

# 생산자(DB에 쓰는 쪽) — 실 저장소엔 항상 있지만, 없어도(격리 테스트 등) 에러는 아니다. 그냥
# "아무것도 생산 안 함"으로 취급한다.
producer_sources=()
[ -d "$root/collector/src" ] && producer_sources+=("$root/collector/src")

# **주석은 배선이 아니다**(check-table-wiring과 같은 규율) — `confidence`가 정확히 javadoc에 걸렸다.
# 전체 줄이 주석인 것만 걷는다. 코드 옆 주석은 건드리지 않는다.
_CODE_ONLY='^[[:space:]]*(//|#|\*|/\*)'

# snake_case → camelCase (암시적 JPA 매핑의 필드명. price_first ↔ priceFirst).
camel() { echo "$1" | sed -E 's/_([a-z])/\U\1/g'; }

# 컬럼(snake 또는 camel)이 주어진 디렉토리들의 프로덕션 **코드**에 나타나는가.
# 디렉토리 목록이 비어 있으면(예: 격리 테스트에 collector/src가 없음) 그냥 "안 나타남" — 에러 아님.
reached_in() {
	local col="$1" cml="$2"
	shift 2
	local dirs=("$@") file
	[ "${#dirs[@]}" -gt 0 ] || return 1
	while IFS= read -r file; do
		[ -n "$file" ] || continue
		if grep -vE "$_CODE_ONLY" "$file" | grep -qP "\b(${col}|${cml})\b"; then
			return 0
		fi
	done < <(grep -rlP "\b(${col}|${cml})\b" "${dirs[@]}" 2>/dev/null | grep -vE '\.test\.|/test/' || true)
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

# 면제 사유가 여전히 유효한가. permanent_reason(선택)은 INTENTIONAL 외에 만료 없이 허용할 사유
# (반쪽 배선의 PRODUCER_ONLY) — 완전히 죽은 컬럼 판정에서는 안 준다(INTENTIONAL만 영구 허용).
exemption_valid() {
	local qid="$1" permanent_reason="${2:-}"
	[ "$qid" = "INTENTIONAL" ] && return 0
	[ -n "$permanent_reason" ] && [ "$qid" = "$permanent_reason" ] && return 0
	q_open "$qid"
}

dead=0
stale=0
for key in "${columns[@]}"; do
	col="${key#*.}"
	cml="$(camel "$col")"
	consumed=false
	produced=false
	reached_in "$col" "$cml" "${consumer_sources[@]}" && consumed=true
	reached_in "$col" "$cml" "${producer_sources[@]}" && produced=true

	if $consumed; then
		# 완전히 배선돼 있다(core·web이 읽는다). 그런데 면제 목록에 있으면 낡은 면제다 — 지워야 한다.
		if [ -n "${excuse[$key]+x}" ]; then
			echo "FAIL: 낡은 면제: '$key'은 이제 코드가 닿는다. allowlist에서 지워라." >&2
			echo "  낡은 면제는 다음 죽은 컬럼을 숨긴다." >&2
			stale=$((stale + 1))
		fi
		continue
	fi

	if $produced; then
		# Q-89 원형 — collector는 쓰는데 core/web은 안 읽는다("반쪽 배선"). 완전히 죽은 것과 다른 카테고리.
		if [ -n "${excuse[$key]+x}" ]; then
			qid="${excuse[$key]}"
			if exemption_valid "$qid" "PRODUCER_ONLY"; then
				continue
			fi
			echo "FAIL: 면제 '$key'가 인용한 $qid가 docs/91에 열려 있지 않다(해소됨?). 면제를 지우거나 Q를 다시 열어라." >&2
			stale=$((stale + 1))
			continue
		fi
		echo "FAIL: 반쪽 배선 '$key' — collector는 쓰는데 core/web 프로덕션 코드가 읽지 않는다(Q-89 원형)." >&2
		echo "  core가 읽게 배선하거나, allowlist에 <Q-ID|PRODUCER_ONLY>로 선언하라." >&2
		dead=$((dead + 1))
		continue
	fi

	# 생산도 소비도 없다 = 완전히 죽은 컬럼. 면제됐는가?
	if [ -n "${excuse[$key]+x}" ]; then
		qid="${excuse[$key]}"
		if exemption_valid "$qid"; then
			continue
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
