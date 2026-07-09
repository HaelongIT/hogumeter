#!/usr/bin/env bash
# `.githooks/pre-commit` 계약 테스트 — 훅이 **실제로 도는지**를 확인한다.
#
#   bash .githooks/pre-commit.test.sh
#
# 훅은 만들어두기 쉽고, 켜지 않으면 도는지 아무도 모른다(`docs/91` Q-42). 게다가 차단 장치는
# "무엇을 막는가"만큼 "무엇을 통과시키는가"가 중요하다 — 오차단은 조용히 작업을 마비시킨다.
#
# 격리: **일회용 git 저장소**에서만 돈다. 이 저장소의 인덱스·워킹트리를 건드리지 않는다.
# 요구: Docker(gitleaks 이미지).

set -euo pipefail

root=$(git rev-parse --show-toplevel)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

pass=0
fail=0

check() { # check <설명> <기대 exit> <실행 결과 exit>
	if [ "$2" = "$3" ]; then
		pass=$((pass + 1))
		echo "  ok    $1"
	else
		fail=$((fail + 1))
		echo "  FAIL  $1 (기대 exit=$2, 실제 exit=$3)" >&2
	fi
}

echo "--- 일회용 저장소 준비 ---"
cp "$root/.gitleaks.toml" "$work/.gitleaks.toml"
mkdir -p "$work/.githooks"
cp "$root/.githooks/pre-commit" "$work/.githooks/pre-commit"
cd "$work"
git init -q
git config core.autocrlf false
git config user.email hook@test
git config user.name hook-test

run_hook() {
	git add -A 2>/dev/null # 임시 저장소의 줄끝 안내는 소음이다
	set +e
	bash .githooks/pre-commit >/dev/null 2>&1
	local code=$?
	set -e
	echo "$code"
}

echo "--- 막아야 할 것 ---"
printf 'curl -s -u admin:s3cr3tP@ssw0rd "https://api.example.com"\n' >app.sh
check "curl -u의 진짜 자격증명은 커밋을 막는다" 1 "$(run_hook)"
rm -f app.sh

echo "--- 통과해야 할 것 (오차단이 더 위험하다) ---"
# .gitleaks.toml의 AND 예외: 이 규칙·이 경로·이 문자열 셋을 모두 만족할 때만.
mkdir -p scripts
printf 'curl -s -u smoke:smoke-pass "$url"\n' >scripts/smoke.sh
check "승인된 스모크 자격증명은 통과한다" 0 "$(run_hook)"

printf '# 그냥 코드다. curl 이야기를 하는 문서일 뿐.\n' >README.md
check "시크릿 없는 평범한 변경은 통과한다" 0 "$(run_hook)"

# 예외는 좁아야 한다 — 같은 문자열이라도 다른 파일이면 잡힌다.
rm -f scripts/smoke.sh README.md
printf 'curl -s -u smoke:smoke-pass "$url"\n' >other.sh
check "같은 문자열이라도 다른 파일이면 막는다(예외가 좁다)" 1 "$(run_hook)"

echo
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS ($pass)"
else
	echo "FAILED: $fail / $((pass + fail))" >&2
	exit 1
fi
