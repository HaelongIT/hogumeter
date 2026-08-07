#!/usr/bin/env bash
# `up-public.sh`의 계약 테스트(BE-06). 실행: bash scripts/up-public.test.sh
#
# 일회용 트리에 fake preflight.sh + fake docker를 만들어 붓는다 — 진짜 스택을 건드리지 않는다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
UP_PUBLIC="$root/scripts/up-public.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0

# fake <preflight exit code> — 일회용 작업 디렉토리를 만들고 fake preflight.sh + fake docker를 채운다.
fake() {
	local r
	r=$(mktemp -d "$work/rXXXXXX")
	mkdir -p "$r/scripts"
	cp "$UP_PUBLIC" "$r/scripts/up-public.sh"
	cat >"$r/scripts/preflight.sh" <<EOF
#!/usr/bin/env bash
echo "fake preflight called: \$*" >>"$r/preflight-calls.log"
exit $1
EOF
	chmod +x "$r/scripts/preflight.sh"
	mkdir -p "$r/bin"
	cat >"$r/bin/docker" <<EOF
#!/usr/bin/env bash
echo "fake docker called: \$*" >>"$r/docker-calls.log"
exit 0
EOF
	chmod +x "$r/bin/docker"
	touch "$r/.env"
	printf '%s' "$r"
}

echo "── preflight 실패 → docker를 부르지 않는다 ──"
r=$(fake 1)
set +e
(cd "$r" && PATH="$r/bin:$PATH" bash scripts/up-public.sh >"$work/out1" 2>&1)
got=$?
set -e
if [ "$got" -ne 0 ]; then
	printf '  PASS  exit=%s (0이 아님)\n' "$got"
else
	printf '  FAIL  expected non-zero exit, got 0\n'
	sed 's/^/        /' "$work/out1"
	fail=1
fi
if [ ! -f "$r/docker-calls.log" ]; then
	printf '  PASS  docker가 호출되지 않았다\n'
else
	printf '  FAIL  preflight 실패에도 docker가 호출됐다: %s\n' "$(cat "$r/docker-calls.log")"
	fail=1
fi

echo "── preflight 성공 → public 프로파일을 올린다 ──"
r=$(fake 0)
set +e
(cd "$r" && PATH="$r/bin:$PATH" bash scripts/up-public.sh >"$work/out2" 2>&1)
got=$?
set -e
if [ "$got" -eq 0 ]; then
	printf '  PASS  exit=0\n'
else
	printf '  FAIL  expected exit=0, got %s\n' "$got"
	sed 's/^/        /' "$work/out2"
	fail=1
fi
if [ -f "$r/docker-calls.log" ] && grep -q -- "--profile public up -d" "$r/docker-calls.log"; then
	printf '  PASS  docker compose --profile public up -d가 호출됐다\n'
else
	printf '  FAIL  docker가 기대한 인자로 호출되지 않았다\n'
	fail=1
fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
