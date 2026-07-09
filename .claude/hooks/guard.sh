#!/usr/bin/env bash
# PreToolUse(Bash) 가드레일 — 실 사이트·외부 API 호출을 막는다(CLAUDE.md §Autonomous 정지조건).
#
# 왜 훅인가: 공식 문서가 직접 권한다 — "Bash permission patterns that try to constrain command
# arguments are fragile... Use PreToolUse hooks: implement a hook that validates URLs in Bash
# commands and blocks disallowed domains." 호스트 단위 판정은 permission 규칙으로 표현할 수 없다.
#
# `git push`는 여기 없다. 그건 permissions.deny("Bash(git push *)")가 처리한다 —
# deny는 프로세스 spawn이 0이고 하위명령별로 매칭돼 `cd x && git push`도 잡는다.
#
# 알려진 한계 (샌드박스가 아니라 백스톱이다. 문서도 같은 경고를 한다):
#   - `sh -c "curl https://..."` 처럼 다른 셸로 감싸면 못 잡는다
#   - `URL=https://... && curl $URL` 처럼 변수로 우회하면 못 잡는다
#   - 리다이렉트(`curl -L https://bit.ly/x`)로 대상 호스트에 도달하면 못 잡는다
#   진짜 강제가 필요하면 OS 샌드박스(docs: /en/sandboxing)를 쓴다.
#
# 규율: 차단은 exit 2 + stderr(사유가 Claude에게 피드백된다). 그 외에는 반드시 exit 0(무의견).
#       매 Bash 호출마다 도므로 서브프로세스를 쓰지 않는다(grep/sed 대신 bash 내장 정규식).

set -u

INPUT="$(cat 2>/dev/null || true)"
[ -z "$INPUT" ] && exit 0

# tool_input.command 만 검사한다. stdin 전체를 훑으면 Bash 도구의 `description` 필드까지
# 매칭돼, 설명에 "뽐뿌"·"curl"을 적었다는 이유로 무해한 `ls`가 차단된다(실제로 겪은 오차단).
case "$INPUT" in
	*'"command"'*) ;;
	*) exit 0 ;;
esac

CMD="${INPUT#*\"command\"*:*\"}" # `"command":"` 이후
CMD="${CMD//\\\"/$'\x01'}"       # 이스케이프된 따옴표를 잠시 치환해 보호
CMD="${CMD%%\"*}"                # 첫 실제 따옴표 앞까지 = 명령 문자열
CMD="${CMD//$'\x01'/\"}"         # 복원
[ -z "$CMD" ] && exit 0

# 네트워크 도구는 **명령 세그먼트의 시작**에 있을 때만 본다. 그래야
# `grep -rn "curl https://www.ppomppu.co.kr" docs/`(문서 검색)가 오차단되지 않는다.
NET_TOOL='(^|[;&|(])[[:space:]]*(curl|wget)([[:space:]]|$)'
TARGET_HOST='(ppomppu\.co\.kr|ruliweb\.com|fmkorea\.com|bunjang\.co\.kr|openapi\.naver\.com|api\.telegram\.org)'

if [[ "$CMD" =~ $NET_TOOL ]] && [[ "$CMD" =~ $TARGET_HOST ]]; then
	echo "차단: 실 사이트·외부 API 호출은 정지조건이다(CLAUDE.md §Autonomous)." >&2
	echo "핫딜 3사·번개장터 크롤링, 네이버 API 실호출, 텔레그램 실전송은 사용자 승인이 필요하다." >&2
	echo "파서·파이프라인 검증은 collector/tests/fixtures/의 golden 파일로 할 것." >&2
	exit 2
fi

exit 0
