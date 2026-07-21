#!/usr/bin/env bash
# `check-dead-columns.sh`의 계약 테스트.  실행: bash scripts/check-dead-columns.test.sh
#
# 일회용 저장소 트리를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(멀쩡히 매핑된 컬럼을 죽었다고 부름)은 사람이 게이트를 꺼 버리게 만든다 → **통과 케이스를 먼저·더 많이**.
# 그리고 이 게이트의 존재 이유(주석은 배선이 아니다)를 **양방향으로** 시험한다 — 주석에만 있는 컬럼은 차단돼야 한다.

set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-dead-columns.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake_root <디렉토리> — 최소 트리(마이그레이션 + 소스 + 보드 + 빈 allowlist)
fake_root() {
	local r="$1"
	mkdir -p "$r/core/src/main/resources/db/migration" "$r/core/src/main/java" "$r/scripts" "$r/docs"
	cat >"$r/docs/91-open-questions.md" <<'MD'
## [열림] Q-9. 아직 막혀 있는 무엇
## [부분해소] Q-28. 일부만 됨
MD
	: >"$r/scripts/dead-columns-allowlist.txt"
}

# ddl <root> <본문> — create table 블록을 표준 형태로 쓴다(컬럼은 한 줄에 하나, `);`로 닫음).
ddl() { printf 'create table t (\n%s\n);\n' "$2" >"$1/core/src/main/resources/db/migration/V1__init.sql"; }

check() { # expected_exit  label  root
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

new_case() { mktemp -d "$work/rXXXXXX"; } # `$(fn)`는 서브셸이라 카운터가 안 돌아온다 — mktemp로 격리

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"

r=$(new_case)
fake_root "$r"
ddl "$r" '    mappedcol text not null'
printf 'class E { @Column(name = "mappedcol") String mappedcol; }\n' >"$r/core/src/main/java/E.java"
check 0 "매핑된 컬럼(스네이크)이 코드에 나타난다" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    price_first bigint not null'
printf 'class E { private long priceFirst; }\n' >"$r/core/src/main/java/E.java" # 암시적 JPA 매핑(camelCase 필드)
check 0 "camelCase 필드로 매핑된 스네이크 컬럼(price_first↔priceFirst)" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    base_price bigint'
printf 'base_price INTENTIONAL 역산 금지 — 항상 null\n' >"$r/scripts/dead-columns-allowlist.txt"
# 주의: allowlist 키는 table.column. 위 t.base_price로 맞춘다.
printf 't.base_price INTENTIONAL 역산 금지\n' >"$r/scripts/dead-columns-allowlist.txt"
check 0 "죽은 컬럼 + INTENTIONAL 면제" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    confidence numeric(3, 2)'
printf 't.confidence Q-9 잠정적으로 죽음(열린 Q)\n' >"$r/scripts/dead-columns-allowlist.txt"
check 0 "죽은 컬럼 + 열린 Q 면제" "$r"

echo "── 차단되어야 함 (exit 1) ──"

r=$(new_case)
fake_root "$r"
ddl "$r" '    deadcol text'
printf 'class E { String other; }\n' >"$r/core/src/main/java/E.java"
check 1 "죽은 컬럼 — 코드가 안 닿고 면제도 없다" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    ghostcol text'
# 이 게이트의 존재 이유: 컬럼이 **주석에만** 있으면 배선이 아니다(confidence가 정확히 javadoc에 걸렸다).
printf 'class E {\n  // ghostcol 은 아직 매핑하지 않았다\n  String other;\n}\n' >"$r/core/src/main/java/E.java"
check 1 "주석에만 있는 컬럼은 여전히 죽었다(주석은 배선이 아니다)" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    confidence numeric(3, 2)'
printf 't.confidence Q-404 없는 Q\n' >"$r/scripts/dead-columns-allowlist.txt" # 해소·부재 Q 인용
check 1 "면제가 인용한 Q가 열려 있지 않다(만료된 면제)" "$r"

r=$(new_case)
fake_root "$r"
ddl "$r" '    livecol text'
printf 'class E { @Column(name = "livecol") String livecol; }\n' >"$r/core/src/main/java/E.java" # 이제 배선됨
printf 't.livecol Q-9 낡은 면제(이미 배선됨)\n' >"$r/scripts/dead-columns-allowlist.txt"
check 1 "낡은 면제 — 컬럼이 이제 배선됐는데 면제가 남아 있다" "$r"

echo ""
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS"
else
	echo "SOME FAILED" >&2
	exit 1
fi
