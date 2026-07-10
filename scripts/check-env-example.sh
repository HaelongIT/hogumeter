#!/usr/bin/env bash
# OPS-01 — `docker-compose.yml`이 읽는 환경변수는 전부 `.env.example`에 적혀 있어야 한다.
#
#   bash scripts/check-env-example.sh [compose.yml] [.env.example]
#
# 왜 필요한가: compose에 `${NEW_VAR:-default}`를 더하고 문서화를 잊으면, 운영자는 그 손잡이가
# 있는 줄도 모른 채 **조용히 기본값으로** 배포한다. 실제로 `CORE_LOG_FORMAT`은 `:-` 때문에
# 빈 값조차 기본값으로 치환돼 "구조화 로그를 끌 수 없는" 상태였다(docs/99 2026-07-10).
# 기동에 실패하면 차라리 낫다 — 조용히 다른 값으로 도는 것이 나쁘다.
#
# 한 방향만 본다: **compose에 있는데 .env.example에 없는 것**만 실패다.
# 반대(`.env.example`에만 있는 것)는 정상이다 — `AWS_*`는 `scripts/offsite-upload.sh`가,
# `TELEGRAM_ALLOWED_CHAT_IDS`는 아직 없는 봇 어댑터가 읽는다(SEC-03).

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
compose="${1:-$root/docker-compose.yml}"
example="${2:-$root/.env.example}"

for file in "$compose" "$example"; do
	[ -f "$file" ] || {
		echo "FAIL: 파일이 없다: $file" >&2
		exit 1
	}
done

# `${VAR}`·`${VAR:-x}`·`${VAR:?}`·`${VAR-x}` 전부 잡는다. 매치가 없으면 grep은 1을 반환한다.
referenced=$(grep -ohE '\$\{[A-Z_][A-Z0-9_]*' "$compose" | sed 's/\${//' | sort -u || true)
documented=$(grep -ohE '^[A-Z_][A-Z0-9_]*=' "$example" | sed 's/=$//' | sort -u || true)

missing=$(comm -23 <(printf '%s\n' "$referenced") <(printf '%s\n' "$documented"))

if [ -n "$missing" ]; then
	echo "FAIL: compose가 읽는데 .env.example에 없는 환경변수:" >&2
	printf '  %s\n' "$missing" >&2
	echo "" >&2
	echo '운영자는 없는 손잡이를 켤 수 없다. .env.example에 기본값·용도와 함께 적을 것.' >&2
	exit 1
fi

echo "ENV OK: compose가 읽는 변수 $(printf '%s\n' "$referenced" | grep -c .)개가 전부 .env.example에 있다"
