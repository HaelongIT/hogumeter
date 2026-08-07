#!/usr/bin/env bash
# `check-ci-coverage.sh`의 계약 테스트.  실행: bash scripts/check-ci-coverage.test.sh
#
# 이 게이트는 **저장소의 실제 스크립트 목록**을 본다(주입할 수 없다). 그래서 바꿔 볼 수 있는 건
# ci.yml뿐이다 — 일회용 사본을 만들어 붓는다. 실제 CI 파일은 건드리지 않는다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-ci-coverage.sh"
CI="$root/.github/workflows/ci.yml"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

check() { # expected_exit  label  ci_file
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

echo "── 차단되어야 함 (exit 1) ──"

# 드릴 하나를 CI에서 빼면 잡아야 한다.
grep -v 'bash scripts/rollback-drill.sh' "$CI" >"$work/no-rollback.yml"
check 1 "드릴 하나가 CI에서 빠졌다 (rollback-drill)" "$work/no-rollback.yml"

# 계약 테스트 하나를 빼도 잡아야 한다.
grep -v 'bash .claude/hooks/guard.test.sh' "$CI" >"$work/no-guard.yml"
check 1 "계약 테스트 하나가 CI에서 빠졌다 (guard.test.sh)" "$work/no-guard.yml"

# **주석은 실행이 아니다.** 이름만 주석에 남기고 실행을 지우면 잡아야 한다.
sed 's|^\( *\)- name: 백업→복원 왕복 리허설.*|\1# backup-drill.sh 는 여기 있었다|; s|^ *run: bash scripts/backup-drill.sh||' \
	"$CI" >"$work/commented.yml"
check 1 "이름이 주석에만 남았다 (restore-drill이 닫힘을 잃는다)" "$work/commented.yml"

check 1 "CI 파일 자체가 없다" "$work/does-not-exist.yml"

echo "── 통과해야 함 (exit 0) — 오차단은 게이트를 꺼지게 만든다 ──"
check 0 "실제 ci.yml" "$CI"

# 1단 닫힘: 직접 호출이 없어도 CI가 부르는 드릴이 부르면 통과한다(restore-drill이 그렇다).
if bash "$CHECK" "$CI" | grep -q '다른 드릴을 통해 1'; then
	printf '  PASS  exit=0  1단 닫힘이 실제로 동작한다 (restore-drill ← backup-drill)\n'
else
	printf '  FAIL  1단 닫힘이 동작하지 않는다\n'
	fail=1
fi

# X-04(코드리뷰 20260806) — 직접 호출 판정(`in_ci`)은 ci.yml에서 주석을 걷어내지만, 1단 닫힘
# 판정(`called_by_ci_script`)은 caller 스크립트 원문을 그대로 grep해 같은 규율이 없었다. 완전히
# 격리된 가짜 저장소로 재현한다(실제 targets 목록을 오염시키지 않기 위해 — root를 통째로 바꾼다).
echo "── 1단 닫힘도 주석은 실행이 아니다 (X-04) ──"

fake_repo() { # <caller 본문> → 격리된 fake root 경로를 찍는다
	local r
	r=$(mktemp -d "$work/rootXXXXXX")
	mkdir -p "$r/scripts" "$r/.github/workflows"
	printf '#!/usr/bin/env bash\necho drill\n' >"$r/scripts/fake-target-drill.sh"
	printf '%s\n' "$1" >"$r/scripts/fake-caller.sh"
	cat >"$r/.github/workflows/ci.yml" <<'YML'
jobs:
  x:
    steps:
      - run: bash scripts/fake-caller.sh
YML
	printf '%s' "$r"
}

r=$(fake_repo 'bash scripts/fake-target-drill.sh')
if bash "$CHECK" "$r/.github/workflows/ci.yml" "$r" >"$work/out-indirect-ok" 2>&1; then
	printf '  PASS  exit=0  실제로 호출하면 1단 닫힘으로 통과\n'
else
	printf '  FAIL  실제 호출인데도 통과하지 못했다\n'
	sed 's/^/        /' "$work/out-indirect-ok"
	fail=1
fi

r=$(fake_repo '# fake-target-drill.sh 는 예전엔 여기서 불렸다')
set +e
bash "$CHECK" "$r/.github/workflows/ci.yml" "$r" >"$work/out-indirect-commented" 2>&1
got=$?
set -e
if [ "$got" -eq 1 ]; then
	printf '  PASS  exit=1  caller 안에서 호출 줄이 주석으로만 남아도 차단된다\n'
else
	printf '  FAIL  expected=1 got=%s  주석만 남은 1단 닫힘을 놓쳤다\n' "$got"
	sed 's/^/        /' "$work/out-indirect-commented"
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
