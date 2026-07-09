#!/usr/bin/env bash
# SessionStart 훅 — 열린 보드를 컨텍스트에 주입한다(stdout이 곧 Claude의 컨텍스트).
#
# CLAUDE.md의 "세션 시작 시 보드를 읽는다"는 산문 지시였고, 실제로 지켜지지 않았다
# (교훈 6건이 쌓이는 동안 축적된 규칙 승격 0건). 읽기를 선택이 아니라 기정사실로 만든다.
# 매처를 생략해 startup·resume·clear·compact 전부에서 발동 → 압축 후에도 보드가 살아남는다.
#
# 규율: 훅 실패가 세션을 방해하면 안 되므로 어떤 경우에도 exit 0.
#       grep -c는 0건 매칭 시 exit 1을 내므로 전부 `|| true`로 흡수한다.

set -u

# CLAUDE_CODE가 주입하는 프로젝트 루트를 우선 쓴다(문서 권장). 없으면 git으로 폴백.
ROOT="${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel 2>/dev/null || echo .)}"
cd "$ROOT" 2>/dev/null || exit 0

DN="working-area/decisions-needed.md"
Q="docs/91-open-questions.md"
L="docs/99-lessons.md"
C="CLAUDE.md"

echo "=== 호구미터 세션 브리핑 (.claude/hooks/session-brief.sh 자동 주입) ==="
echo

# ── 열린 결정: 가장 위험하다(AI 임의 확정 금지) ──────────────────────
echo "[열린 결정 — AI 임의 확정 금지]  $DN"
open_decisions="$(grep -E '^## D-[0-9]+\.' "$DN" 2>/dev/null | sed 's/^## /  /' || true)"
if [ -n "$open_decisions" ]; then
	echo "$open_decisions"
	echo "  ※ 관련 작업 착수 전 반드시 확인. 확정은 사람만 한다."
else
	echo "  없음."
fi
echo

# ── 열린 기술 보류: 제목만(본문 223줄은 주입하지 않는다) ─────────────
# `<제목>`은 파일 상단 "항목 양식" 코드블록의 템플릿이라 실제 항목이 아니다 — 제외.
open_q="$(grep '^## \[열림\]' "$Q" 2>/dev/null | grep -v '<제목>' | sed 's/^## \[열림\] /  /' || true)"
q_count="$(printf '%s\n' "$open_q" | grep -c . || true)"
echo "[열린 기술 보류]  $Q — ${q_count:-0}건 (제목만, 상세는 파일을 읽을 것)"
[ -n "$open_q" ] && printf '%s\n' "$open_q"
echo

# ── 보드 무결성: 같은 Q가 "해소 stub"과 "[열림]"에 동시에 있으면 보드가 거짓말한다 ──
# (실제로 Q-41이 그랬다. 다음 세션이 이미 고친 결함을 다시 고치려 든다.)
dupes="$(
	{
		grep -o '^## \[열림\] Q-[0-9]*\.' "$Q" 2>/dev/null | grep -o 'Q-[0-9]*'
		grep -o '^_(Q-[0-9]*\.' "$Q" 2>/dev/null | grep -o 'Q-[0-9]*'
	} | sort | uniq -d || true
)"
if [ -n "$dupes" ]; then
	echo "[⚠ 보드 모순] 아래 항목이 '해소 stub'과 '[열림]'에 동시에 있다 — 하나를 지울 것:"
	printf '  %s\n' $dupes
	echo
fi

# ── 기록 드리프트: 관측된 실패(승격 누락)를 매 세션 눈에 보이게 ──────
lessons="$(grep -c '^### 20' "$L" 2>/dev/null || true)"
rules="$(sed -n '/^## 축적된 규칙/,/^## /p' "$C" 2>/dev/null | grep -c '^- ' || true)"
echo "[기록 드리프트]"
echo "  $L 교훈 ${lessons:-0}건  vs  $C 축적된 규칙 ${rules:-0}건"
echo "  최근 교훈 3건:"
grep '^### 20' "$L" 2>/dev/null | tail -3 | sed 's/^### /    /' || true
echo "  → 새 교훈이 보편 규칙이면 CLAUDE.md 축적된 규칙으로,"
echo "     언어·디렉토리 한정이면 .claude/rules/<scope>.md 로 승격할 것."
echo

# ── loose-end 라우팅: 성격별로 정확히 한 보드 ────────────────────────
echo "[loose-end 라우팅] 말로 끝내지 않는다"
echo "  사람이 정할 것 → working-area/decisions-needed.md   (임의 확정 금지)"
echo "  잠정값으로 진행 → docs/91-open-questions.md         (잠정값 + 재개 트리거)"
echo "  운영 배포 갭   → working-area/pre-deploy-checklist.md"
echo "  확정된 결정    → working-area/decision-log.md"
echo "  재사용 교훈    → docs/99-lessons.md"
echo "  외부 사이트 실측 → docs/98-field-notes.md"

exit 0
