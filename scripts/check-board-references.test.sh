#!/usr/bin/env bash
# check-board-references.sh의 차단/통과 계약. 게이트를 만들면 **양방향**으로 시험한다 —
# 미차단(없는 Q를 통과)과 오차단(멀쩡한 인용을 막음) 둘 다.

set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
gate="$here/check-board-references.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fail=0

# 케이스마다 새 디렉토리를 mktemp로 만든다 — 카운터는 서브셸에서 안 돌아온다(99: 2026-07-10).
new_case() { mktemp -d "$work/rXXXXXX"; }

# fake_root <디렉토리> — 최소 트리(보드 + 소스 디렉토리)
fake_root() {
	local r="$1"
	mkdir -p "$r/core/src/main" "$r/collector/src" "$r/scripts" "$r/docs"
	cat >"$r/docs/91-open-questions.md" <<'MD'
## [열림] Q-9. 아직 막혀 있는 무엇
## [해소 2026-07-22] Q-10. 닫힌 것
- 본문에서만 언급되는 Q-11도 보드는 알고 있다(제목이 지워진 해소 항목).
MD
}

check() { # check <기대 exit> <설명> <root>
	local expected="$1" what="$2" root="$3" actual
	bash "$gate" "$root" >/dev/null 2>&1
	actual=$?
	if [ "$actual" -eq "$expected" ]; then
		echo "  PASS  exit=$actual  $what"
	else
		echo "  FAIL  exit=$actual (기대 $expected)  $what" >&2
		fail=1
	fi
}

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"

r=$(new_case)
fake_root "$r"
printf '// 잠정값 — docs/91 Q-9 참조\nclass A {}\n' >"$r/core/src/main/A.java"
check 0 "열린 Q 인용" "$r"

r=$(new_case)
fake_root "$r"
printf '# 이렇게 된 이유는 Q-10에서 정해졌다\n' >"$r/collector/src/a.py"
check 0 "해소된 Q 인용도 정상이다 — 근거는 닫혀도 근거다" "$r"

r=$(new_case)
fake_root "$r"
printf '# 제목이 지워진 Q-11 — 보드 본문에만 남아 있다\n' >"$r/collector/src/a.py"
check 0 "본문에만 남은 Q도 보드가 아는 Q다(제목만 보면 대량 오차단)" "$r"

r=$(new_case)
fake_root "$r"
printf 'echo "가짜 보드에 Q-404를 쓴다"\n' >"$r/scripts/check-something.test.sh"
check 0 "계약 테스트(*.test.sh) 안의 가짜 Q는 데이터다 — 검사 대상 아님" "$r"

echo "── 차단되어야 함 (exit 1) ──"

r=$(new_case)
fake_root "$r"
printf '// 잠정 — docs/91 Q-74 참조\nclass A {}\n' >"$r/core/src/main/A.java"
check 1 "보드에 없는 Q를 인용한다 (2026-07-23 실사고: Q-74가 코드에만 있었다)" "$r"

r=$(new_case)
fake_root "$r"
printf '# 상수 100은 잠정 — Q-999\n' >"$r/collector/src/market.py"
check 1 "collector 쪽 인용도 본다" "$r"

r=$(new_case)
fake_root "$r"
printf 'Q-888  면제 사유\n' >"$r/scripts/some-allowlist.txt"
check 1 "allowlist의 인용도 본다(다른 게이트가 보는 자리와 겹쳐도 존재는 여기서 본다)" "$r"

r=$(new_case)
fake_root "$r"
: >"$r/docs/91-open-questions.md" # 보드가 비었다
printf '// Q-9\nclass A {}\n' >"$r/core/src/main/A.java"
check 1 "보드가 비면 형식 변경을 의심하고 멈춘다(조용히 전부 통과하지 않는다)" "$r"

echo ""
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS"
else
	echo "SOME FAILED" >&2
	exit 1
fi
