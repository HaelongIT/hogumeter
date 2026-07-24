#!/usr/bin/env bash
# `check-repository-readers.sh`의 계약 테스트.  실행: bash scripts/check-repository-readers.test.sh
#
# 일회용 트리에 자바 파일을 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(멀쩡히 호출되는 메서드를 죽었다고 부름)은 사람이 게이트를 꺼 버리게 만든다.
# **격리는 카운터가 아니라 `mktemp`로 한다** — `$(fake …)`는 서브셸이다(2026-07-10 교훈).

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-repository-readers.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

PKG="core/src/main/java/dev/hogumeter/core/adapter/persistence"
APP="core/src/main/java/dev/hogumeter/core/application"

fake() { # <repo 본문> <소비자 본문> [allowlist 줄]
	local r
	r=$(mktemp -d "$work/rXXXXXX")
	mkdir -p "$r/$PKG" "$r/$APP" "$r/scripts" "$r/docs"
	printf '## [열림] Q-9. 아직 막혀 있는 무엇\n' >"$r/docs/91-open-questions.md"
	printf '%s\n' "$1" >"$r/$PKG/ThingRepository.java"
	printf '%s\n' "$2" >"$r/$APP/Consumer.java"
	if [ $# -ge 3 ]; then
		printf '%s\n' "$3" >"$r/scripts/repository-readers-allowlist.txt"
	fi
	printf '%s' "$r"
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

REPO='public interface ThingRepository extends JpaRepository<ThingEntity, Long> {
	List<ThingEntity> findByProductId(Long productId);
}'

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"
check 0 "프로덕션 소비자가 실제로 부른다" "$(fake "$REPO" \
	'class Consumer { ThingRepository things; void go() { things.findByProductId(1L); } }')"

check 0 "커스텀 메서드가 없는 리포지토리는 대상이 아니다" \
	"$(fake 'public interface ThingRepository extends JpaRepository<ThingEntity, Long> { }' 'class Consumer {}')"

check 0 "호출자 0이지만 열린 Q로 선언됨" "$(fake "$REPO" 'class Consumer {}' \
	'ThingRepository.findByProductId  Q-9  아직 소비자가 없다')"

echo "── 차단되어야 함 (exit 1) ──"
# 이게 이 게이트의 존재 이유: `product_axis`는 엔티티가 있어 table-wiring을 통과했지만 쓰기 전용이었다.
check 1 "호출자 0인데 선언도 없다 (테이블이 쓰기 전용이다)" "$(fake "$REPO" 'class Consumer {}')"

# **테스트는 호출자가 아니다** — 소비자 파일이 없고 테스트만 부르는 상황을 흉내낸다.
r=$(fake "$REPO" 'class Consumer {}')
mkdir -p "$r/core/src/test/java"
printf 'class RepoTest { void t() { things.findByProductId(1L); } }\n' >"$r/core/src/test/java/RepoTest.java"
check 1 "테스트만 부르는 것은 호출자가 아니다" "$r"

# **주석은 호출이 아니다.** 전체 줄이 주석인 것만 걷는다 — 코드 뒤에 붙은 `//`는 걷지 않는다
# (그건 코드 줄이고, 거기에 호출이 있으면 진짜 호출이다). javadoc·`//` 줄이 흔한 형태다.
check 1 "javadoc 속 호출은 호출이 아니다" "$(fake "$REPO" \
	'class Consumer {
	ThingRepository things;
	/**
	 * things.findByProductId(1L) 을 부를 예정이다.
	 */
	void go() {}
}')"

check 1 "한 줄 주석(//) 속 호출도 호출이 아니다" "$(fake "$REPO" \
	'class Consumer {
	ThingRepository things;
	// things.findByProductId(1L);
	void go() {}
}')"

# **수신자 타입으로 스코프한다** — 다른 리포지토리의 동명 메서드 호출이 죽은 것을 가리면 안 된다.
r=$(fake "$REPO" 'class Consumer { OtherRepository others; void go() { others.findByProductId(1L); } }')
printf 'public interface OtherRepository extends JpaRepository<O, Long> {\n\tList<O> findByProductId(Long id);\n}\n' \
	>"$r/$PKG/OtherRepository.java"
check 1 "다른 리포지토리의 동명 메서드 호출은 이 메서드의 호출자가 아니다" "$r"

check 1 "만료된 면제: 인용한 Q가 보드에 열려 있지 않다" "$(fake "$REPO" 'class Consumer {}' \
	'ThingRepository.findByProductId  Q-77  없는 Q를 인용했다')"

check 1 "낡은 면제: 이제 호출되는데 목록에 남아 있다" "$(fake "$REPO" \
	'class Consumer { ThingRepository things; void go() { things.findByProductId(1L); } }' \
	'ThingRepository.findByProductId  Q-9  이미 호출된다')"

check 1 "리포지토리 디렉토리가 없다" "$work/does-not-exist"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

# 면제가 실제로 집계되는지 — 카운트가 안 늘면 allowlist가 죽은 것이다. **실제 저장소가 아니라
# 합성 시나리오**로 본다(위 "호출자 0이지만 열린 Q로 선언됨"과 같은 fixture 재사용) — 실제 저장소는
# 완전히 배선돼 면제가 0건일 수 있고(2026-07-24 실측: 그랬다), 그때 이 메타 테스트가 "allowlist가
# 안 읽힌다"고 오판하면 안 된다. 저장소가 좋아질수록 게이트가 깨지는 건 거꾸로 된 계약이다.
excused_root=$(fake "$REPO" 'class Consumer {}' \
	'ThingRepository.findByProductId  Q-9  아직 소비자가 없다')
if bash "$CHECK" "$excused_root" | grep -qE '미사용 선언 [1-9][0-9]*'; then
	printf '  PASS  exit=0  면제가 실제로 집계된다(allowlist가 읽힌다)\n'
else
	printf '  FAIL  allowlist가 읽히지 않는다\n'
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
