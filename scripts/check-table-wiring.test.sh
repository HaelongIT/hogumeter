#!/usr/bin/env bash
# `check-table-wiring.sh`의 계약 테스트.  실행: bash scripts/check-table-wiring.test.sh
#
# 일회용 저장소 트리를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(멀쩡히 배선된 테이블을 죽었다고 부름)은 사람이 게이트를 꺼 버리게 만든다.
# 그래서 **통과해야 하는 경우를 먼저·더 많이** 시험한다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-table-wiring.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake_root <디렉토리> — 최소 트리(마이그레이션 1개 + 보드 + 빈 allowlist)
fake_root() {
	local r="$1"
	mkdir -p "$r/core/src/main/resources/db/migration" "$r/core/src/main/java" "$r/scripts" "$r/docs"
	cat >"$r/docs/91-open-questions.md" <<'MD'
## [열림] Q-9. 아직 막혀 있는 무엇
## [열림] Q-30. 숫자 접두 충돌 확인용
MD
	: >"$r/scripts/table-wiring-allowlist.txt"
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

new_case() { # 다음 **일회용** root 경로를 찍는다
	# `$(new_case)`는 명령 치환 = **서브셸**이라 `case_no=$((case_no+1))`이 부모로 돌아오지 않는다.
	# 카운터로 이름을 지으면 모든 케이스가 `r1`을 재사용하고, 앞 케이스가 남긴 마이그레이션 파일이
	# 뒤 케이스에 섞여 **의도하지 않은 이유로 통과**한다(2026-07-10 실측).
	mktemp -d "$work/rXXXXXX"
}

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"

r=$(new_case)
fake_root "$r"
printf 'create table product (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'class P { @Table(name = "product") }\n' >"$r/core/src/main/java/P.java"
check 0 "배선된 테이블만 있다" "$r"

r=$(new_case)
fake_root "$r"
printf 'create table price_history (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'price_history  Q-9  아직 막혔다\n' >"$r/scripts/table-wiring-allowlist.txt"
check 0 "미배선 + 열린 Q로 선언됨" "$r"

r=$(new_case)
fake_root "$r"
# `create table if not exists` · 대문자 DDL · 여러 V 파일에 걸친 테이블
printf 'CREATE TABLE variant (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'create table if not exists purchase (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V2__purchase.sql"
printf 'x variant y purchase z\n' >"$r/core/src/main/java/P.java"
check 0 "대소문자·if not exists·V2까지 모두 인식한다" "$r"

echo "── 차단되어야 함 (exit 1) ──"

r=$(new_case)
fake_root "$r"
printf 'create table global_setting (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
check 1 "아무도 쓰지도 읽지도 않는데 선언도 없다" "$r"

r=$(new_case)
fake_root "$r"
printf 'create table price_history (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'price_history  Q-77  없는 Q를 인용했다\n' >"$r/scripts/table-wiring-allowlist.txt"
check 1 "만료된 면제: 인용한 Q가 보드에 열려 있지 않다" "$r"

r=$(new_case)
fake_root "$r"
printf 'create table product (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'class P { @Table(name = "product") }\n' >"$r/core/src/main/java/P.java"
printf 'product  Q-9  이미 배선됐는데 면제가 남아 있다\n' >"$r/scripts/table-wiring-allowlist.txt"
check 1 "낡은 면제: 배선된 테이블이 목록에 남아 있다" "$r"

# **테스트는 호출자가 아니다.** 이 게이트의 존재 이유 — `FlywayMigrationTest`가 죽은 테이블의
# 존재를 GREEN으로 잠그고 있었다. 테스트 파일의 언급을 배선으로 세면 게이트가 무의미해진다.
r=$(new_case)
fake_root "$r"
mkdir -p "$r/core/src/test/java" "$r/web/src"
printf 'create table review_queue_item (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'assertTable("review_queue_item");\n' >"$r/core/src/test/java/FlywayMigrationTest.java"
printf 'const t = "review_queue_item"\n' >"$r/web/src/present.test.ts"
check 1 "테스트 파일의 언급은 배선이 아니다" "$r"

# **주석은 배선이 아니다.** `DealEventEntity`의 javadoc은 "applied_conditions는 미매핑"이라고
# 적어 두고 있었다 — 그 문장을 배선으로 읽으면 게이트가 자기 존재 이유를 놓친다.
r=$(new_case)
fake_root "$r"
printf 'create table price_history (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
cat >"$r/core/src/main/java/Dead.java" <<'JAVA'
/**
 * price_history 는 아직 아무도 쓰지 않는다(미배선).
 */
class Dead {}
JAVA
check 1 "javadoc의 언급은 배선이 아니다" "$r"

r=$(new_case)
fake_root "$r"
printf 'create table price_history (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf '// price_history 는 미배선이다\n# 파이썬 주석도 마찬가지\nclass X {}\n' >"$r/core/src/main/java/X.java"
check 1 "한 줄 주석(// · #)도 배선이 아니다" "$r"

# 반대로 **주석 옆의 진짜 코드**는 배선이다(오차단 방지).
r=$(new_case)
fake_root "$r"
printf 'create table product (id bigserial);\n' >"$r/core/src/main/resources/db/migration/V1__init.sql"
printf '// product 는 아래에서 매핑된다\nclass P { @Table(name = "product") }\n' >"$r/core/src/main/java/P.java"
check 0 "주석이 있어도 코드가 배선하면 통과한다" "$r"

# `deal_event_source`가 `deal_event`의 배선인 척하면 안 된다(밑줄은 단어 문자).
r=$(new_case)
fake_root "$r"
printf 'create table deal_event (id bigserial);\ncreate table deal_event_source (id bigserial);\n' \
	>"$r/core/src/main/resources/db/migration/V1__init.sql"
printf 'class S { @Table(name = "deal_event_source") }\n' >"$r/core/src/main/java/S.java"
check 1 "접두 충돌: deal_event_source는 deal_event를 배선하지 않는다" "$r"

r=$(new_case)
check 1 "마이그레이션 디렉토리가 없다" "$r/does-not-exist"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

# 면제 목록이 살아 있는지 — 실제 저장소에서 선언이 실제로 세어져야 한다.
# 개수는 allowlist가 늘고 줄며 바뀌므로(하드코딩하면 매번 깨진다) "1 이상 집계됨"만 본다.
if bash "$CHECK" | grep -qE '미배선 선언 [1-9][0-9]*'; then
	printf '  PASS  exit=0  면제가 실제로 집계된다(allowlist가 읽힌다)\n'
else
	printf '  FAIL  면제 집계가 동작하지 않는다 (allowlist가 읽히고 있나?)\n'
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
