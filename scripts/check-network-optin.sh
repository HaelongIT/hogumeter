#!/usr/bin/env bash
# 정지조건("실사이트 크롤링·외부 API 실호출")을 **스크립트 안에서도** 강제한다.
#
#   bash scripts/check-network-optin.sh [root]
#
# 왜 필요한가: `.claude/hooks/guard.sh`는 PreToolUse 훅이라 **에이전트가 친 명령 문자열만** 본다.
# `bash scripts/x.sh` 안에서 `curl https://www.ppomppu.co.kr`을 하면 훅은 아무것도 못 본다
# (docs/91 Q-60). 완화책은 "네트워크로 나가는 스크립트는 자기 자신에게도 opt-in 게이트를 건다"였는데,
# **그게 지켜지는지 확인하는 법이 없었다** — 확인 절차가 없는 항목은 아무도 검증하지 않는다.
#
# 판정: 네트워크 명령(`curl`·`wget`·`aws`)이 있는 줄에 **외부 URL 리터럴**이 있으면,
# 그 파일은 opt-in 환경변수(`ALLOW_REAL_ROBOTS`·`COLLECTOR_ALLOW_NETWORK`)를 참조해야 한다.
#
# 로컬은 외부가 아니다: `127.0.0.1` · `localhost` · `example.invalid` · `minio`.
# 변수로 조립한 URL(`"$WEB/api"`)은 리터럴이 아니라 잡히지 않는다 — 이 게이트는 **필요조건**이다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
allowlist="$root/scripts/network-optin-allowlist.txt"

mapfile -t targets < <(
	find "$root/scripts" "$root/.claude/hooks" "$root/.githooks" \
		-maxdepth 1 -type f \( -name '*.sh' -o -name 'pre-commit' \) -print 2>/dev/null | sort
)
[ "${#targets[@]}" -gt 0 ] || {
	echo "FAIL: 검사 대상 스크립트를 하나도 찾지 못했다(경로가 바뀌었나?)" >&2
	exit 1
}

# 면제: URL이 **데이터**인 파일(가드가 그 URL을 차단하는지 시험하는 계약 테스트).
declare -A excused=()
if [ -f "$allowlist" ]; then
	while read -r name _rest; do
		case "$name" in '' | '#'*) continue ;; esac
		excused["$name"]=1
	done <"$allowlist"
fi

external_urls() { # 주석을 걷고, 네트워크 명령이 있는 줄에서 외부 URL만 뽑는다
	grep -vE '^[[:space:]]*#' "$1" |
		grep -E '\b(curl|wget|aws)\b' |
		grep -oE 'https?://[A-Za-z0-9.-]+' |
		grep -vE '127\.0\.0\.1|localhost|example\.invalid|minio' |
		sort -u || true
}

fail=0
guarded=0
clean=0
for target in "${targets[@]}"; do
	name=$(basename "$target")
	urls=$(external_urls "$target")
	if [ -z "$urls" ]; then
		clean=$((clean + 1))
		continue
	fi

	if [ -n "${excused[$name]:-}" ]; then
		guarded=$((guarded + 1))
		continue
	fi

	if grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK' "$target"; then
		guarded=$((guarded + 1))
		continue
	fi

	echo "FAIL: opt-in 게이트 없이 외부로 나간다: ${target#"$root/"}" >&2
	printf '  외부 URL: %s\n' "$(echo "$urls" | tr '\n' ' ')" >&2
	echo "  PreToolUse 훅은 스크립트 **안**을 보지 못한다(docs/91 Q-60)." >&2
	echo "  ALLOW_REAL_ROBOTS 같은 opt-in을 스크립트가 스스로 확인하거나," >&2
	echo "  URL이 데이터일 뿐이면 scripts/network-optin-allowlist.txt에 사유와 함께 선언하라." >&2
	fail=1
done

[ "$fail" -eq 0 ] || exit 1
echo "NETWORK OPTIN OK: 스크립트 ${#targets[@]}개 (외부 URL 없음 ${clean} · opt-in 또는 선언 ${guarded})"
