#!/usr/bin/env bash
# `check-domain-consumers.sh`의 계약 테스트.  실행: bash scripts/check-domain-consumers.test.sh
#
# 일회용 트리에 자바 파일을 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# 오차단(멀쩡히 쓰이는 클래스를 죽었다고 부름)은 사람이 게이트를 꺼 버리게 만든다.
# **격리는 `mktemp`로 한다** — `$(fake …)`는 서브셸이다(2026-07-10 교훈).

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-domain-consumers.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

DOM="core/src/main/java/dev/hogumeter/core/domain/alert"
APP="core/src/main/java/dev/hogumeter/core/application"

fake() { # <도메인 클래스 본문> <소비자 본문> [allowlist 줄]
	local r
	r=$(mktemp -d "$work/rXXXXXX")
	mkdir -p "$r/$DOM" "$r/$APP" "$r/scripts" "$r/docs"
	printf '## [열림] Q-9. 아직 막혀 있는 무엇\n' >"$r/docs/91-open-questions.md"
	printf '%s\n' "$1" >"$r/$DOM/Thing.java"
	printf '%s\n' "$2" >"$r/$APP/Consumer.java"
	if [ $# -ge 3 ]; then
		printf '%s\n' "$3" >"$r/scripts/domain-consumers-allowlist.txt"
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

THING='public class Thing { public boolean decide() { return true; } }'

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"
check 0 "프로덕션 소비자가 타입을 쓴다" "$(fake "$THING" 'class Consumer { Thing thing; }')"
check 0 "소비처 0이지만 열린 Q로 선언됨" "$(fake "$THING" 'class Consumer {}' 'Thing  Q-9  아직 배선 전')"

# enum·interface는 **데이터/계약**이다. Jackson·JPA가 역직렬화로 쓰므로 리터럴 소비처가 없을 수 있다.
check 0 "enum은 대상이 아니다" "$(fake 'public enum Thing { A, B }' 'class Consumer {}')"
check 0 "interface는 대상이 아니다" "$(fake 'public interface Thing { void go(); }' 'class Consumer {}')"
# record는 **필드만 있을 때만** 데이터다 — 정준 접근자 밖 메서드(행동)가 있으면 보통 클래스처럼 본다.
check 0 "필드만 있는 record는 대상이 아니다" "$(fake 'public record Thing(long id) { }' 'class Consumer {}')"

echo "── 차단되어야 함 (exit 1) ──"
# 이게 이 게이트의 존재 이유: 순수 도메인은 단위 테스트만으로 GREEN이 되어 죽어도 신호가 없다.
check 1 "소비처 0인데 선언도 없다" "$(fake "$THING" 'class Consumer {}')"

# 이게 이번에 메운 구멍: 행동(상태전이·가드)이 있는 record는 소비처 0이면 잡혀야 한다 — `Listing`
# (USED-02)이 이 모양으로 죽어 있었다. 정준 접근자를 넘는 메서드가 신호다.
check 1 "행동이 있는 record인데 소비처가 없으면 차단된다(Listing류 결함)" "$(fake \
	'public record Thing(long id, String status) {
	public Thing transition(String target) {
		if (!status.equals("A")) {
			throw new IllegalStateException("nope");
		}
		return new Thing(id, target);
	}
}' 'class Consumer {}')"

# **테스트는 호출자가 아니다.**
r=$(fake "$THING" 'class Consumer {}')
mkdir -p "$r/core/src/test/java"
printf 'class ThingTest { void t() { new Thing().decide(); } }\n' >"$r/core/src/test/java/ThingTest.java"
check 1 "테스트만 쓰는 것은 소비가 아니다" "$r"

# **주석은 소비가 아니다.**
check 1 "javadoc 속 언급은 소비가 아니다" "$(fake "$THING" \
	'class Consumer {
	/**
	 * Thing 을 언젠가 주입받을 예정이다.
	 */
	void go() {}
}')"
check 1 "한 줄 주석(//) 속 언급도 소비가 아니다" "$(fake "$THING" \
	'class Consumer {
	// Thing thing;
	void go() {}
}')"

# **죽은 소비자는 소비자가 아니다.** 면제된 클래스가 유일한 참조자면 그 클래스도 죽었다.
r=$(fake "$THING" 'class Consumer { Thing thing; }' 'Consumer  Q-9  이것도 죽었다')
check 1 "면제된(죽은) 클래스가 유일한 소비자면 그것도 죽은 것이다" "$r"

check 1 "만료된 면제: 인용한 Q가 보드에 열려 있지 않다" "$(fake "$THING" 'class Consumer {}' \
	'Thing  Q-77  없는 Q를 인용했다')"
check 1 "낡은 면제: 이제 소비되는데 목록에 남아 있다" \
	"$(fake "$THING" 'class Consumer { Thing thing; }' 'Thing  Q-9  이미 소비된다')"

# X-02(코드리뷰 20260806) — `open_question()`이 `## ` 헤더만 보고 `[해소 ...]`도 열린 것으로
# 오판했다. 해소된 Q를 인용한 면제는 그 순간부터 만료돼야 한다.
r=$(fake "$THING" 'class Consumer {}' 'Thing  Q-9  아직 배선 전')
printf '## [해소 2026-07-25] Q-9. 아직 막혀 있는 무엇\n' >"$r/docs/91-open-questions.md"
check 1 "만료된 면제: 인용한 Q가 이미 [해소]됐다" "$r"

check 1 "도메인 디렉토리가 없다" "$work/does-not-exist"

echo "── 실제 저장소 (exit 0) ──"
if bash "$CHECK" >"$work/real" 2>&1; then
	printf '  PASS  exit=0  %s\n' "$(cat "$work/real")"
else
	printf '  FAIL  실제 저장소가 계약을 어긴다\n'
	sed 's/^/        /' "$work/real"
	fail=1
fi

# 면제가 실제로 집계되는지 — 카운트가 안 늘면 allowlist가 죽은 것이다. **실제 저장소가 아니라
# 합성 시나리오**로 본다(위 "소비처 0이지만 열린 Q로 선언됨" fixture 재사용) — 실제 저장소는
# 면제가 0건일 수 있고(도메인이 전부 소비되면 그렇다), 그때 이 메타 테스트가 "allowlist가 안
# 읽힌다"고 오판하면 안 된다. 저장소가 좋아질수록 게이트가 깨지는 건 거꾸로 된 계약이다
# (check-repository-readers.test.sh가 같은 이유로 같은 수정을 받았다, 2026-07-24).
excused_root=$(fake "$THING" 'class Consumer {}' 'Thing  Q-9  아직 배선 전')
if bash "$CHECK" "$excused_root" | grep -qE '미배선 선언 [1-9][0-9]*'; then
	printf '  PASS  exit=0  면제가 실제로 집계된다(allowlist가 읽힌다)\n'
else
	printf '  FAIL  allowlist가 읽히지 않는다\n'
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
