#!/usr/bin/env bash
# `check-conditional-props.sh`의 계약 테스트.  실행: bash scripts/check-conditional-props.test.sh
#
# 일회용 트리를 만들어 붓는다 — 진짜 저장소를 건드리지 않는다.
# **차단 케이스는 2026-07-22에 실제로 우리를 문 형태**(`${TELEGRAM_ENABLED:-}`)를 그대로 쓴다.
# 오차단(멀쩡한 설정을 막음)은 사람이 게이트를 꺼 버리므로 통과 케이스를 먼저·더 많이 시험한다.

set -uo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-conditional-props.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake_root <dir> <compose에 넣을 environment 줄> <java 소스 내용>
fake_root() {
	local r="$1" env_line="$2" java="$3"
	mkdir -p "$r/core/src/main/java"
	cat >"$r/docker-compose.yml" <<YAML
services:
  core:
    image: x
    environment:
      $env_line
YAML
	printf '%s\n' "$java" >"$r/core/src/main/java/A.java"
}

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

new_case() { mktemp -d "$work/rXXXXXX"; } # `$(fn)`는 서브셸이라 카운터가 안 돌아온다

COND='@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")'

echo "── 통과해야 함 (exit 0) ──"

r=$(new_case)
fake_root "$r" 'OTHER: ${OTHER:-x}' "$COND"
check 0 "compose가 그 변수를 안 넘긴다(부재 → matchIfMissing 정상)" "$r"

r=$(new_case)
fake_root "$r" 'TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-false}' "$COND"
check 0 "비지 않은 기본값(:-false)" "$r"

# 주석은 배선이 아니다. **진짜 조건부 하나 + 주석 하나**로 시험한다 — 주석만 두면 "하나도 못 찾음"
# 가드에 정당하게 걸려 무엇을 검증했는지 알 수 없다(그 케이스는 아래 차단 케이스가 따로 본다).
r=$(new_case)
fake_root "$r" 'TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-}' \
	"$(printf '@ConditionalOnProperty(name = "core.pipeline.enabled", havingValue = "true")\n// %s' "$COND")"
check 0 "주석 속 @ConditionalOnProperty는 세지 않는다(빈 기본값이어도 통과)" "$r"

r=$(new_case)
fake_root "$r" 'CORE_PIPELINE_ENABLED: ${CORE_PIPELINE_ENABLED:-true}' \
	'@ConditionalOnProperty(name = "core.pipeline.enabled", havingValue = "true", matchIfMissing = true)'
check 0 "점이 여럿인 속성명도 환경변수로 옳게 변환된다(core.pipeline.enabled → CORE_PIPELINE_ENABLED)" "$r"

echo "── 차단되어야 함 (exit 1) ──"

r=$(new_case)
fake_root "$r" 'TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-}' "$COND"
check 1 "빈 기본값 \${X:-} — 2026-07-22에 실제로 core를 죽인 형태" "$r"

r=$(new_case)
fake_root "$r" 'TELEGRAM_ENABLED: ${TELEGRAM_ENABLED}' "$COND"
check 1 "기본값 없음 \${X} — 미설정이면 빈 문자열이 된다" "$r"

r=$(new_case)
fake_root "$r" 'CORE_PIPELINE_ENABLED: ${CORE_PIPELINE_ENABLED:-}' \
	'@ConditionalOnProperty(name = "core.pipeline.enabled", havingValue = "true", matchIfMissing = true)'
check 1 "대안 빈이 없는 조건부(파이프라인)는 **조용히 안 뜬다** — 더 위험한 변종" "$r"

r=$(new_case)
fake_root "$r" 'TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-false}' 'class A {}'
check 1 "@ConditionalOnProperty를 하나도 못 찾으면 FAIL(형식 변경 감지)" "$r"

echo ""
if [ "$fail" -eq 0 ]; then
	echo "ALL PASS"
else
	echo "SOME FAILED" >&2
	exit 1
fi
