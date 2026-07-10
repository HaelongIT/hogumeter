#!/usr/bin/env bash
# PreToolUse(Bash) 가드레일 — 실 사이트·외부 API 호출을 막는다(CLAUDE.md §Autonomous 정지조건).
#
# 왜 훅인가: 공식 문서가 직접 권한다 — "Bash permission patterns that try to constrain command
# arguments are fragile... Use PreToolUse hooks: implement a hook that validates URLs in Bash
# commands and blocks disallowed domains." 호스트 단위 판정은 permission 규칙으로 표현할 수 없다.
#
# **파괴적 `git push`도 여기서 막는다**(2026-07-10). 예전엔 permissions.deny("Bash(git push *)")가
# 모든 푸시를 막았으나, 사용자 결정으로 **일반 푸시는 허용하고 파괴적 형태만 금지**로 좁혔다.
# deny에 인자 패턴을 쓰지 않는 이유는 위와 같다 — 공식 문서가 "fragile하니 훅을 쓰라"고 한다.
# `git push origin main`은 non-fast-forward면 git이 알아서 거절하므로 안전하다. 남의 커밋을
# 지우는 것(force·삭제·mirror·prune)만 막는다. `main`은 상대 개발자도 직접 커밋한다.
#
# 알려진 한계 (샌드박스가 아니라 백스톱이다. 문서도 같은 경고를 한다):
#   - `sh -c "curl https://..."` 처럼 다른 셸로 감싸면 못 잡는다
#   - `URL=https://... && curl $URL` 처럼 변수로 우회하면 못 잡는다
#   - 리다이렉트(`curl -L https://bit.ly/x`)로 대상 호스트에 도달하면 못 잡는다
#   - `bash scripts/x.sh` 안의 호출은 못 본다(docs/91 Q-60) — 스크립트가 스스로 게이트를 건다
#   - **도구 이름으로 스코프된다.** 이 훅은 settings.json의 `matcher`가 지목한 도구에만 붙는다.
#     `Bash`만 적어 두면 `PowerShell` 도구로 같은 명령을 보내는 순간 아무것도 막지 않는다.
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

# ── 파괴적 `git push` ──────────────────────────────────────────────────────
# `push`가 **git의 서브커맨드**일 때만 본다. 그래야 `git commit -m "... --force ..."`나
# `grep -rn "git push --force" docs/`가 오차단되지 않는다(오차단은 개발을 마비시킨다).
#   `git( -옵션 | 이름=값)* push` 만 매칭 — `git -c core.x=y push`, `git --no-pager push` 포함.
GIT_PUSH='^[[:space:]]*git([[:space:]]+(-[^[:space:]]+|[a-zA-Z_][a-zA-Z0-9_.]*=[^[:space:]]+))*[[:space:]]+push([[:space:]]|$)'

# 남의 커밋을 지우는 형태. `-[a-z]*[fd][a-z]*`는 묶음 단축 옵션(`-f`·`-uf`·`-d`)까지 잡는다.
# `--dry-run`(-n)·`-u`·`--follow-tags`는 f/d를 포함하지 않아 통과한다.
PUSH_DESTRUCTIVE='(^|[[:space:]])(--force(-with-lease|-if-includes)?(=[^[:space:]]*)?|--mirror|--prune|--delete|-[a-zA-Z]*[fd][a-zA-Z]*)([[:space:]]|$)'
# refspec으로도 지운다: `+main`(강제) · `:main`(원격 브랜치 삭제). `main:main`은 무해하다.
PUSH_REFSPEC='(^|[[:space:]])[+:][^[:space:]]*'

# 세그먼트 단위로 본다. `git push origin main; git push --force`처럼 여러 push가 섞이면
# 문자열 전체를 한 덩어리로 보는 방식은 뒤쪽을 놓친다.
SEGMENTS="${CMD//&&/$'\n'}"
SEGMENTS="${SEGMENTS//||/$'\n'}"
SEGMENTS="${SEGMENTS//;/$'\n'}"
SEGMENTS="${SEGMENTS//|/$'\n'}"
SEGMENTS="${SEGMENTS//&/$'\n'}"
SEGMENTS="${SEGMENTS//(/$'\n'}"

while IFS= read -r SEGMENT; do
	[[ "$SEGMENT" =~ $GIT_PUSH ]] || continue
	ARGS="${SEGMENT#*push}" # push 뒤 인자만 본다
	if [[ "$ARGS" =~ $PUSH_DESTRUCTIVE ]] || [[ "$ARGS" =~ $PUSH_REFSPEC ]]; then
		echo "차단: 파괴적 git push는 정지조건이다(CLAUDE.md §Autonomous)." >&2
		echo "force-push·원격 참조 삭제(--force/-f/--delete/-d/--mirror/--prune/+refspec/:refspec)는" >&2
		echo "main에 직접 커밋하는 상대 개발자의 커밋을 지운다. 정말 필요하면 사용자가 직접 친다." >&2
		echo "일반 푸시(git push origin main)는 허용된다 — non-fast-forward면 git이 알아서 거절한다." >&2
		exit 2
	fi
done <<<"$SEGMENTS"

exit 0
