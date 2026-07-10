#!/usr/bin/env bash
# SEC-01 / REL-04 — **커밋되면 안 되는 것이 정말 무시되는가.**
#
#   bash scripts/check-gitignore.sh [저장소루트]
#
# 왜 필요한가: `.gitignore`는 지금 옳다. 그런데 아무것도 그걸 강제하지 않는다 — `backups/` 한 줄이
# 지워지면 다음 `git add -A`가 **DB 덤프를 통째로 커밋한다.** gitleaks는 gzip 안을 보지 못하므로
# CI도 통과한다.
#
# 반대편도 본다: `.env.example`은 **커밋돼야 한다**(운영자가 손잡이를 알아야 한다). 오차단은
# 조용히 문서를 지운다.
#
# 그리고 "무시된다"와 "이미 커밋돼 있지 않다"는 다른 사건이다 — 한 번 커밋된 파일은 `.gitignore`가
# 무시하지 않는다. 추적 목록도 함께 검사한다.

set -euo pipefail

root="${1:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
fail=0

# 커밋되면 안 되는 것. 실제 파일이 없어도 `check-ignore`는 경로 규칙만 본다.
MUST_IGNORE=(
	.env
	.env.prod
	.env.local
	backups/hogumeter-20260101T000000Z.sql.gz
	backups/backup.log
	.claude/settings.local.json
	docker-compose.override.yml
)

# 커밋돼야 하는 것. 오차단하면 운영자가 손잡이를 모른다.
MUST_NOT_IGNORE=(
	.env.example
	docker-compose.yml
	scripts/backup.sh
)

for path in "${MUST_IGNORE[@]}"; do
	if ! git -C "$root" check-ignore -q "$path"; then
		echo "FAIL: 무시돼야 하는데 무시되지 않는다: $path" >&2
		fail=1
	fi
done

for path in "${MUST_NOT_IGNORE[@]}"; do
	if git -C "$root" check-ignore -q "$path"; then
		echo "FAIL: 커밋돼야 하는데 무시된다: $path" >&2
		fail=1
	fi
done

# 이미 추적 중인 위험 파일. `.gitignore`는 **이미 커밋된 파일을 무시하지 않는다.**
tracked=$(git -C "$root" ls-files -- '*.sql.gz' '.env' '.env.*' ':!.env.example' 2>/dev/null || true)
if [ -n "$tracked" ]; then
	echo "FAIL: 이미 추적 중인 위험 파일이 있다(.gitignore는 이걸 되돌리지 않는다):" >&2
	printf '  %s\n' "$tracked" >&2
	fail=1
fi

[ "$fail" -eq 0 ] || exit 1
echo "GITIGNORE OK: 위험 경로 ${#MUST_IGNORE[@]}개 차단 · 필수 경로 ${#MUST_NOT_IGNORE[@]}개 통과 · 추적된 위험 파일 0"
