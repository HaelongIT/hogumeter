#!/usr/bin/env bash
# "호출자 0인 순수 함수"를 클래스 단위로 잡는다.
#
#   bash scripts/check-domain-consumers.sh [root]
#
# 왜 필요한가: 순수 도메인은 IO가 없어 **단위 테스트만으로 GREEN**이 된다. 그래서 프로덕션에서
# 아무도 안 불러도 아무 신호가 없다. 오늘까지 손으로 셋을 찾았다:
#   `effective_interval_with_robots`(collector, robots Crawl-delay가 한 번도 안 지켜졌다) ·
#   `Purchase.expire()`(관찰이 영원히 안 끝났다) · `FollowUpEvaluator`(AL-03 후속 알림이 없다).
# **테스트는 호출자가 아니다**(CLAUDE.md).
#
# 판정: `core/.../domain/**/*.java`의 클래스 이름을 **프로덕션 코드**(main/java, 자기 자신 제외)가
# 한 번이라도 언급하는가. enum·interface·record는 데이터라 대상이 아니고, `package-info`도 아니다.
#
# ⚠️ **필요조건일 뿐이다**: "이름이 나타난다"는 "호출된다"가 아니다. 다만 도메인 클래스는
# 생성자 주입·new로 쓰이므로 이름조차 없으면 확실히 죽었다. 주석은 걷어낸다.
#
# **죽은 소비자는 소비자가 아니다.** 면제된(=아무도 안 쓰는) 클래스가 유일한 참조자면 그 클래스도
# 죽은 것이다 — `ReportCardCalculator`가 `DealSets`를 살려 보이게 했다(지금은 다른 소비자가 넷 있다).
# 면제 목록은 그래서 **한 번만** 걷어내면 충분하다: 면제는 사람이 승인한 목록이라 연쇄가 깊지 않다.
#
# 면제는 `domain-consumers-allowlist.txt`에 **열린 Q-ID와 함께** 선언한다 — Q가 닫히면 면제도 죽는다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
domain="$root/core/src/main/java/dev/hogumeter/core/domain"
sources="$root/core/src/main/java"
allowlist="$root/scripts/domain-consumers-allowlist.txt"
board="$root/docs/91-open-questions.md"

[ -d "$domain" ] || {
	echo "FAIL: 도메인 디렉토리가 없다: $domain" >&2
	exit 1
}

mapfile -t files < <(find "$domain" -type f -name '*.java' ! -name 'package-info.java' | sort)
[ "${#files[@]}" -gt 0 ] || {
	echo "FAIL: 도메인 클래스를 하나도 찾지 못했다(경로가 바뀌었나?)" >&2
	exit 1
}

declare -A excuse=()
if [ -f "$allowlist" ]; then
	while read -r name qid _rest; do
		case "$name" in '' | '#'*) continue ;; esac
		excuse["$name"]="$qid"
	done <"$allowlist"
fi

open_question() { # 인용한 Q가 docs/91에 **열려 있는가**
	[ -f "$board" ] || return 1
	grep -qE "^## .*${1}\." "$board"
}

# 주석은 소비가 아니다. 전체 줄이 주석인 것만 걷는다.
_CODE_ONLY='^[[:space:]]*(//|\*|/\*)'

fail=0
alive=0
excused=0
skipped=0

for file in "${files[@]}"; do
	name=$(basename "$file" .java)

	# enum·interface·record는 데이터다 — Jackson·JPA가 역직렬화로 쓰므로 리터럴 호출자가 없을 수 있다.
	if grep -vE "$_CODE_ONLY" "$file" | grep -qE "^public (enum|interface|record) ${name}\b"; then
		skipped=$((skipped + 1))
		continue
	fi

	consumers=0
	while IFS= read -r user; do
		[ -n "$user" ] || continue
		# **죽은 소비자는 소비자가 아니다.** 면제 목록에 있는(=아무도 안 쓰는) 클래스가 유일한
		# 참조자면 그 클래스도 죽은 것이다 — `ReportCardCalculator`가 `DealSets`를 살려 보이게 했다.
		user_name=$(basename "$user" .java)
		[ -n "${excuse[$user_name]:-}" ] && continue
		if grep -vE "$_CODE_ONLY" "$user" | grep -qP "\b${name}\b"; then
			consumers=$((consumers + 1))
		fi
	done < <(
		grep -rl "\b${name}\b" "$sources" --include='*.java' 2>/dev/null |
			grep -v "/${name}\.java$" || true
	)

	qid="${excuse[$name]:-}"

	if [ "$consumers" -gt 0 ]; then
		if [ -n "$qid" ]; then
			echo "FAIL: 낡은 면제: '$name'은 이제 소비된다. allowlist에서 지워라." >&2
			echo "  낡은 면제는 다음 결함을 숨긴다." >&2
			fail=1
		else
			alive=$((alive + 1))
		fi
		continue
	fi

	if [ -z "$qid" ]; then
		echo "FAIL: 프로덕션 소비처가 0인 도메인 클래스: $name" >&2
		echo "  단위 테스트만으로 GREEN이 되므로 죽어 있어도 아무 신호가 없다 — **테스트는 호출자가 아니다.**" >&2
		echo "  배선하거나, scripts/domain-consumers-allowlist.txt에 열린 Q-ID와 함께 선언하라." >&2
		fail=1
	elif ! open_question "$qid"; then
		echo "FAIL: 만료된 면제: '$name'이 인용한 $qid 가 docs/91에 열려 있지 않다." >&2
		echo "  막고 있던 것이 해소됐다면 이제 배선할 때다." >&2
		fail=1
	else
		excused=$((excused + 1))
	fi
done

[ "$fail" -eq 0 ] || exit 1
echo "DOMAIN CONSUMERS OK: 클래스 $((alive + excused))개 (소비됨 ${alive} · 미배선 선언 ${excused} · 데이터 타입 제외 ${skipped})"
