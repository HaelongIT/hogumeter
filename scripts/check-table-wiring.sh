#!/usr/bin/env bash
# "쓰기만 하는 테이블 · 읽기만 하는 테이블"의 상류 — **아무도 쓰지도 읽지도 않는 테이블.**
#
#   bash scripts/check-table-wiring.sh [root]
#
# 왜 필요한가: `alert_policy`는 읽히기만 해서 목표가 알림이 발화할 수 없었고, `review_queue_item`은
# 쌓이기만 해서 볼 수가 없었다. 둘 다 **모든 테스트가 GREEN**인 채였다. 감사를 손으로 돌려 찾았지만
# **다음 테이블을 만들 때 또 잊는다.** 사람의 기억을 장치로 바꾼다.
#
# 판정: 마이그레이션이 만든 테이블 이름이 **프로덕션 코드**(core/src/main/java · collector/src ·
# web/src, 테스트 제외)에 한 번이라도 나타나는가. 나타나지 않으면 그 테이블은 죽어 있다.
#
# 면제는 `scripts/table-wiring-allowlist.txt`에 **열린 Q-ID와 함께** 선언한다. 게이트는
#   ① 인용한 Q가 docs/91에 실제로 **열려 있는지**(해소되면 면제 만료 → 다시 묻는다)
#   ② 면제된 테이블이 그새 배선되지 않았는지(낡은 면제는 다음 결함을 숨긴다)
# 를 함께 본다. 변명이 코드보다 오래 살지 못하게 하는 장치다.
#
# 한계(의도): "이름이 나타난다"는 배선의 **필요조건**이지 충분조건이 아니다. 읽기만/쓰기만 하는
# 테이블은 여기서 걸리지 않는다 — 그건 사람이 소비처·생산자를 코드에서 지목해야 한다(CLAUDE.md).

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
migrations="$root/core/src/main/resources/db/migration"
allowlist="$root/scripts/table-wiring-allowlist.txt"
board="$root/docs/91-open-questions.md"

[ -d "$migrations" ] || {
	echo "FAIL: 마이그레이션 디렉토리가 없다: $migrations" >&2
	exit 1
}

# 테이블 목록의 정본은 마이그레이션이다. 범위를 열어 둔다(`V*__*.sql` 전부) — 특정 파일을 지목하는
# 순간 그 지목이 스냅샷이 되고, V3가 추가되면 조용히 빠진다.
mapfile -t tables < <(
	find "$migrations" -maxdepth 1 -type f -name 'V*__*.sql' -print0 |
		sort -z |
		xargs -0 -r grep -rhoiP 'create table (if not exists )?\K[a-z_]+' |
		sort -u
)
[ "${#tables[@]}" -gt 0 ] || {
	echo "FAIL: 마이그레이션에서 테이블을 하나도 찾지 못했다(DDL 형식이 바뀌었나?)" >&2
	exit 1
}

# 프로덕션 코드만 본다. 테스트는 죽은 테이블의 존재도 GREEN으로 잠근다
# (`FlywayMigrationTest`가 실제로 `price_history`·`global_setting`을 그렇게 잠그고 있었다).
sources=()
for dir in "$root/core/src/main/java" "$root/collector/src" "$root/web/src"; do
	[ -d "$dir" ] && sources+=("$dir")
done
[ "${#sources[@]}" -gt 0 ] || {
	echo "FAIL: 프로덕션 소스 디렉토리를 하나도 찾지 못했다: $root" >&2
	exit 1
}

wired() { # 테이블 이름이 프로덕션 코드에 나타나는가
	local table="$1" hits
	# `\b`가 밑줄을 단어 문자로 보므로 deal_event가 deal_event_source에 오탐하지 않는다.
	hits=$(grep -rlP "\b${table}\b" "${sources[@]}" 2>/dev/null | grep -vE '\.test\.|/test/' || true)
	[ -n "$hits" ]
}

# 면제 목록: "<테이블> <Q-ID> <이유>". 주석·빈 줄은 건너뛴다.
declare -A excuse=()
if [ -f "$allowlist" ]; then
	while read -r table qid _rest; do
		case "$table" in '' | '#'*) continue ;; esac
		excuse["$table"]="$qid"
	done <"$allowlist"
fi

open_question() { # 인용한 Q가 docs/91에 **열려 있는가**. 해소된 Q는 `## ` 헤더가 아니라 각주로 남는다.
	[ -f "$board" ] || return 1
	grep -qE "^## .*${1}\." "$board"
}

fail=0
wired_count=0
excused_count=0

for table in "${tables[@]}"; do
	qid="${excuse[$table]:-}"

	if wired "$table"; then
		if [ -n "$qid" ]; then
			echo "FAIL: 낡은 면제: '$table'은 이제 배선돼 있다. allowlist에서 지워라." >&2
			echo "  낡은 면제는 다음 결함을 숨긴다 — 그 줄이 남아 있는 한 이 테이블은 다시 죽어도 조용하다." >&2
			fail=1
		else
			wired_count=$((wired_count + 1))
		fi
		continue
	fi

	if [ -z "$qid" ]; then
		echo "FAIL: 아무도 쓰지도 읽지도 않는 테이블: '$table'" >&2
		echo "  프로덕션 코드(core/src/main/java · collector/src · web/src)에 이름조차 없다." >&2
		echo "  배선하거나, scripts/table-wiring-allowlist.txt에 열린 Q-ID와 함께 선언하라." >&2
		fail=1
	elif ! open_question "$qid"; then
		echo "FAIL: 만료된 면제: '$table'이 인용한 $qid 가 docs/91에 열려 있지 않다." >&2
		echo "  막고 있던 것이 해소됐다면 이제 배선할 때다. 변명이 코드보다 오래 살면 안 된다." >&2
		fail=1
	else
		excused_count=$((excused_count + 1))
	fi
done

[ "$fail" -eq 0 ] || exit 1
echo "TABLE WIRING OK: 테이블 ${#tables[@]}개 (배선 ${wired_count} · 미배선 선언 ${excused_count})"
