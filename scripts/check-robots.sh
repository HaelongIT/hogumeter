#!/usr/bin/env bash
# SEC-08 실 robots.txt 대조 — `pre-deploy §F`가 사람에게 시키는 그 일.
#
#   ALLOW_REAL_ROBOTS=1 bash scripts/check-robots.sh
#
# 핫딜 3사의 `/robots.txt`만 **각 1회** 조회한다. 목록 페이지는 건드리지 않는다.
# 출력은 `docs/98-field-notes.md`에 그대로 붙일 수 있는 블록이다.
#
# ⚠️ 이 스크립트는 **실 사이트로 나간다.** 그래서:
#   - `ALLOW_REAL_ROBOTS=1` 없이는 아무것도 하지 않는다(도구가 스스로 게이트를 건다).
#   - `.claude/hooks/guard.sh`는 **Bash 명령 문자열만** 본다. `bash scripts/…` 안의 호출은
#     훅에 보이지 않는다(docs/91 Q-60). 에이전트가 이걸 opt-in과 함께 돌리면 정지조건 위반이다.
#     **사람이 돌린다.**
#
# DISALLOW가 하나라도 있으면 exit 1 — "확인 완료"가 아니라 "사람이 결정해야 함"이다.
#
# 리허설: `bash scripts/check-robots-drill.sh` (로컬 서버, 실 사이트 미접촉)

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$root/collector"

exec uv run python -m collector.tools.robots_report
