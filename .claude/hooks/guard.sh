#!/usr/bin/env bash
# PreToolUse(Bash) 가드레일 — CLAUDE.md가 이미 금지한 것을 물리적으로 막는다.
#
# 근거: 공식 문서 "An instruction like 'never edit .env' in CLAUDE.md or a skill is
# a request, not a guarantee... If a rule must hold every time, make it a hook."
# 새 정책이 아니라 기존 정지조건(CLAUDE.md §Autonomous, §Git)의 강제다.
#
# 차단 대상은 둘뿐:
#   1) git push        — "푸시는 사용자 직접. 에이전트는 커밋까지만"
#   2) 실 네트워크 호출 — "외부 실발송·실행(실사이트 크롤링·외부 API 실호출)"
#
# 일부러 차단하지 않는 것: 브랜치 전환·머지. CLAUDE.md는 "임의" 전환을 금지하나,
# 훅은 '임의'와 '사용자가 시킨 것'을 구분하지 못한다. 판단이 필요한 규칙은 프롬프트의 몫.
#
# 규율: 차단은 exit 2 + stderr(사유가 Claude에게 피드백된다). 그 외에는 반드시 exit 0
#       (= 무의견. 기존 권한 흐름을 그대로 탄다). 매 Bash 호출마다 도므로 빨라야 한다.

set -u

INPUT="$(cat 2>/dev/null || true)"
[ -z "$INPUT" ] && exit 0

# 명령 세그먼트의 시작에 있는 `git push`만 잡는다. JSON의 "command":" 직후,
# 문장 시작, 또는 ; & | ( 뒤. 이렇게 앵커를 두면 커밋 메시지 본문에서 `git push`를
# 인용해도(백틱 뒤) 오차단되지 않는다.
if printf '%s' "$INPUT" | grep -qE '(^|["；;&|(]|&&|\|\|)[[:space:]]*git[[:space:]]+push'; then
	echo "차단: git push는 사용자가 직접 한다(CLAUDE.md §Git — 에이전트는 커밋까지만)." >&2
	echo "커밋만 하고, 푸시는 사용자에게 요청할 것." >&2
	exit 2
fi

# 실 네트워크 호출: 네트워크 도구 AND 대상 호스트가 동시에 있을 때만.
# 둘 중 하나만으로는 막지 않는다 — `grep -rn api.telegram.org docs/`는 통과해야 한다.
NET_TOOL='(^|[[:space:]"；;&|(])(curl|wget|httpie)([[:space:]]|$)'
TARGET_HOST='(ppomppu\.co\.kr|ruliweb\.com|fmkorea\.com|bunjang\.co\.kr|openapi\.naver\.com|api\.telegram\.org)'

if printf '%s' "$INPUT" | grep -qE "$NET_TOOL" && printf '%s' "$INPUT" | grep -qE "$TARGET_HOST"; then
	echo "차단: 실 사이트·외부 API 호출은 정지조건이다(CLAUDE.md §Autonomous)." >&2
	echo "핫딜 3사·번개장터 크롤링, 네이버 API 실호출, 텔레그램 실전송은 사용자 승인이 필요하다." >&2
	echo "파서·파이프라인 검증은 tests/fixtures/의 golden 파일로 할 것." >&2
	exit 2
fi

exit 0
