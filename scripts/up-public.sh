#!/usr/bin/env bash
# BE-06(코드리뷰 20260806) — `--profile public`(caddy, 인터넷 공개 노출)이 `preflight.sh prod`
# 통과에 프로그램적으로 묶여 있지 않았다. 운영자가 preflight를 건너뛰거나
# `WEB_BASIC_AUTH_HTPASSWD`를 빠뜨린 채 `docker compose --profile public up -d`를 직접 치면,
# 인증 없는 상태가 그대로 인터넷에 뜬다(발견은 `docker compose logs web`을 사람이 직접 grep해야만
# 가능했다). 이 래퍼는 preflight prod를 **강제**한 뒤에만 public 프로파일을 올린다.
#
#   bash scripts/up-public.sh [.env경로] [-- docker compose 추가 인자...]
#
# preflight가 FAIL이면 아무것도 올리지 않고 종료한다(exit 1). 나머지 인자는 그대로
# `docker compose --profile public up -d`에 전달한다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
cd "$root"

envfile="${1:-.env}"
if [ "${1:-}" != "" ] && [ "${1#-}" = "$1" ]; then
	shift # 첫 인자가 옵션(-로 시작)이 아니면 .env 경로로 소비
fi

echo "── up-public: preflight prod 먼저 ──"
if ! bash scripts/preflight.sh prod "$envfile"; then
	echo "FAIL: preflight prod가 실패했습니다 — public 프로파일을 올리지 않습니다." >&2
	echo "  WEB_BASIC_AUTH_HTPASSWD 등 위 FAIL 항목을 고친 뒤 다시 실행하세요." >&2
	exit 1
fi

echo "── preflight 통과 — docker compose --profile public up -d ──"
exec docker compose --profile public up -d "$@"
