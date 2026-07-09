#!/usr/bin/env bash
# guard.sh 계약 테스트.  실행: bash .claude/hooks/guard.test.sh
#
# 이 가드는 **모든 Bash 호출**을 막을 수 있다. 그래서 "무엇을 막는가"보다
# **"무엇을 통과시켜야 하는가"**가 더 중요하다 — 오차단은 개발을 마비시킨다.
# 실제로 초판은 아래 [오차단 회귀] 4건을 전부 막았다.
#
# 페이로드에 `git push` 등의 문자열이 필요하지만, 명령줄에 리터럴로 쓰면
# 가드가 이 테스트 자신을 막을 수 있다. 그래서 문자열을 조립해 쓴다.

GUARD="$(dirname "$0")/guard.sh"
fail=0

check() { # expected_exit  json  label
	printf '%s' "$2" | bash "$GUARD" >/dev/null 2>&1
	local got=$?
	if [ "$got" -eq "$1" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$3"
	else
		printf '  FAIL  expected=%s got=%s  %s\n' "$1" "$got" "$3"
		fail=1
	fi
}

echo "── 차단되어야 함 (exit 2) ──"
check 2 '{"tool_input":{"command":"curl https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"}}' "뽐뿌 크롤링"
check 2 '{"tool_input":{"command":"wget https://www.fmkorea.com/hotdeal"}}'                        "펨코 크롤링"
check 2 '{"tool_input":{"command":"curl https://bbs.ruliweb.com/market/board/1020"}}'             "루리웹 크롤링"
check 2 '{"tool_input":{"command":"curl https://api.bunjang.co.kr/api/1/find_v2.json?q=iphone"}}' "번개 API"
check 2 '{"tool_input":{"command":"curl https://openapi.naver.com/v1/search/shop.json"}}'         "네이버 API 실호출"
check 2 '{"tool_input":{"command":"curl -X POST https://api.telegram.org/bot1/sendMessage"}}'     "텔레그램 실전송"
check 2 '{"tool_input":{"command":"echo hi; curl https://api.bunjang.co.kr/x"}}'                  "세미콜론 뒤 curl"
check 2 '{"tool_input":{"command":"cd /tmp && wget https://www.ppomppu.co.kr/"}}'                 "체이닝 뒤 wget"

echo "── 오차단 회귀 (exit 0) — 초판이 전부 막았던 것들 ──"
GP="git$(printf ' ')push"
check 0 "$(printf '{"tool_input":{"command":"grep -rn \\"%s\\" docs/"}}' "$GP")"                   "grep으로 문구 검색"
check 0 "$(printf '{"tool_input":{"command":"echo \\"%s\\""}}' "$GP")"                             "echo로 문자열 출력"
check 0 '{"tool_input":{"command":"grep -rn \"curl https://www.ppomppu.co.kr\" docs/98-field-notes.md"}}' "문서에서 curl 예시 검색"
check 0 '{"tool_input":{"command":"ls collector/tests/fixtures/"},"description":"뽐뿌 www.ppomppu.co.kr fixture를 curl 없이 확인"}' "description 오염(무해한 ls)"

echo "── 통과해야 함 (exit 0) — 평소 개발 명령 ──"
check 0 '{"tool_input":{"command":"uv run pytest"}}'                                       "pytest"
check 0 '{"tool_input":{"command":"cd collector && uv run pytest -q"}}'                    "체이닝 pytest"
check 0 '{"tool_input":{"command":"curl http://localhost:8080/api/v1/variants/1/benchmark"}}' "로컬 API 검증"
check 0 '{"tool_input":{"command":"curl https://example.com"}}'                            "무관 호스트"
check 0 '{"tool_input":{"command":"cat .claude/hooks/guard.sh"}}'                          "가드 스크립트 읽기"
check 0 '{"tool_input":{"command":"grep -rn api.telegram.org docs/"}}'                     "호스트 문자열 grep"
check 0 '{"tool_input":{"command":"git status --short"}}'                                  "git status"
check 0 ''                                                                                 "빈 입력"
check 0 '{"tool_name":"Bash"}'                                                             "command 키 없음"

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
