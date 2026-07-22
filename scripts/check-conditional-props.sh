#!/usr/bin/env bash
# 조건부 빈을 가르는 설정에 **빈 문자열이 흘러들지 않는지** 본다.
#
#   bash scripts/check-conditional-props.sh [root]
#
# 왜 필요한가(2026-07-22 실사고): compose가 `TELEGRAM_ENABLED: ${TELEGRAM_ENABLED:-}`로 **빈 문자열**을
# 넘겼다. `@ConditionalOnProperty`는 빈 값을 "부재"가 아니라 **"존재하는데 값이 다름"**으로 읽는다
# (`matchIfMissing`은 부재일 때만 적용된다). 그래서 스텁(havingValue=false)도 실발송(havingValue=true)도
# **둘 다 안 뜨고** AlertSender 빈이 사라져 core가 기동 실패했다 — `.env`가 없는 CI에서만 터져
# smoke·backup-drill이 통째로 죽었고 로컬에선 재현되지 않았다.
#
# 더 나쁜 변종이 잠복해 있다: `core.pipeline.enabled`는 `matchIfMissing=true`에 **대안 빈이 없다.**
# 빈 값이 들어가면 크래시도 없이 **파이프라인이 조용히 안 뜬다**(틱이 멈춘 걸 아무도 모른다).
#
# 판정: core의 `@ConditionalOnProperty(name = "x.y")` 각각에 대해, compose가 그 환경변수
# (Spring 완화 바인딩: `x.y` -> `X_Y`)를 **빈 기본값으로 넘기면** FAIL.
#   - compose가 아예 안 넘긴다 -> OK(부재이므로 matchIfMissing이 정상 적용된다)
#   - 비지 않은 기본값(`${X:-false}`)으로 넘긴다 -> OK
#   - `${X:-}`(빈 기본값) 또는 `${X}`(기본값 없음, 미설정 시 빈 문자열) -> FAIL

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
compose="$root/docker-compose.yml"
sources="$root/core/src/main/java"

[ -f "$compose" ] || {
	echo "FAIL: compose 파일이 없다: $compose" >&2
	exit 1
}
[ -d "$sources" ] || {
	echo "FAIL: core 소스 디렉토리가 없다: $sources" >&2
	exit 1
}

# `@ConditionalOnProperty(name = "telegram.enabled", ...)` 에서 속성 이름만 뽑는다.
# 전체 줄이 주석인 것은 뺀다 — 주석 속 예시는 배선이 아니다(docs/99 "이름이 나타난다 != 실행된다").
mapfile -t props < <(
	grep -rhE '^[^*/]*@ConditionalOnProperty' "$sources" --include='*.java' |
		grep -oE 'name = "[^"]+"' |
		sed -E 's/name = "([^"]+)"/\1/' |
		sort -u
)
[ "${#props[@]}" -gt 0 ] || {
	echo "FAIL: @ConditionalOnProperty를 하나도 찾지 못했다(형식이 바뀌었나?)" >&2
	exit 1
}

# Spring 완화 바인딩: `telegram.bot-token` -> `TELEGRAM_BOT_TOKEN`
env_name_of() { printf '%s' "$1" | tr '.-' '__' | tr '[:lower:]' '[:upper:]'; }

bad=0
checked=0
for prop in "${props[@]}"; do
	env_name="$(env_name_of "$prop")"
	checked=$((checked + 1))
	# compose가 이 변수를 넘기는가. 넘긴다면 어떤 기본값으로?
	if ! grep -qE "\\\$\\{${env_name}[:}-]" "$compose"; then
		continue # compose가 안 넘긴다 = 부재 = matchIfMissing이 정상 동작
	fi
	if grep -qE "\\\$\\{${env_name}:-\\}" "$compose"; then
		echo "FAIL: '$prop'는 조건부 빈을 가르는데 compose가 \${${env_name}:-}로 **빈 문자열**을 넘긴다." >&2
		echo "  빈 값은 '부재'가 아니라 '값이 다름'이라 두 갈래가 동시에 탈락한다 — 유효한 기본값을 주라(예: :-false)." >&2
		bad=$((bad + 1))
		continue
	fi
	if grep -qE "\\\$\\{${env_name}\\}" "$compose"; then
		echo "FAIL: '$prop'를 compose가 \${${env_name}}(기본값 없음)로 넘긴다 — 미설정이면 빈 문자열이 된다." >&2
		echo "  조건부 빈을 가르는 값은 항상 유효해야 한다. \${${env_name}:-<기본값>} 으로 바꿔라." >&2
		bad=$((bad + 1))
	fi
done

if [ "$bad" -eq 0 ]; then
	echo "CONDITIONAL PROPS OK: 조건부 속성 ${checked}개 — compose가 빈 문자열을 넘기지 않는다"
else
	echo "CONDITIONAL PROPS FAILED: $bad" >&2
	exit 1
fi
