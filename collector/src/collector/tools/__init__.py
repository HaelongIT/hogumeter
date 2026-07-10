"""운영자가 손으로 돌리는 도구들 — 수집 루프의 일부가 아니다.

`robots_report`는 실 사이트에 나간다. 그래서 `ALLOW_REAL_ROBOTS=1` opt-in을 스스로 요구한다.
`.claude/hooks/guard.sh`는 **Bash 명령 문자열만** 보므로 `bash scripts/x.sh` 안의 호출은 못 본다 —
훅은 사각을 갖는다. 그래서 네트워크로 나가는 스크립트는 **자기 자신에게도 게이트를 건다**(docs/91 Q-60).
"""
