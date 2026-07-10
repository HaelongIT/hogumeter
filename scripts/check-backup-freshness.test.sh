#!/usr/bin/env bash
# `check-backup-freshness.sh`의 계약 테스트.  실행: bash scripts/check-backup-freshness.test.sh
#
# 이 게이트가 오차단하면 사람은 cron에서 그것을 빼 버린다. 그러면 백업 침묵을 다시 아무도 못 본다.
# 그래서 **무엇을 통과시켜야 하는가**를 먼저·더 많이 시험한다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
CHECK="$root/scripts/check-backup-freshness.sh"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

fail=0
case_no=0

# 유효한 gzip 한 덩어리. 빈 파일·깨진 파일과 구별하려면 진짜 gzip이어야 한다.
dump() { # 경로  [나이(시간)]
	printf 'CREATE TABLE product ();\n' | gzip -9 >"$1"
	# `[ … ] && touch`로 쓰면 조건이 거짓일 때 함수가 1을 반환하고 `set -e`가 스크립트를 죽인다.
	if [ $# -ge 2 ]; then
		touch -d "$2 hours ago" "$1"
	fi
}

check() { # expected_exit  label  setup_fn
	case_no=$((case_no + 1))
	local dir="$work/c$case_no"
	mkdir -p "$dir"
	"$3" "$dir"
	set +e
	bash "$CHECK" "$dir" 26 >"$work/out" 2>&1
	local got=$?
	set -e
	if [ "$got" -eq "$1" ]; then
		printf '  PASS  exit=%s  %s\n' "$got" "$2"
	else
		printf '  FAIL  expected=%s got=%s  %s\n' "$1" "$got" "$2"
		sed 's/^/        /' "$work/out"
		fail=1
	fi
}

empty_dir() { :; }
no_dumps() { touch "$1/backup.log" "$1/README"; }
stale() { dump "$1/hogumeter-old.sql.gz" 30; }
truncated() { : >"$1/hogumeter-cut.sql.gz"; }
corrupt() { printf 'not gzip at all' >"$1/hogumeter-bad.sql.gz"; }
fresh() { dump "$1/hogumeter-now.sql.gz"; }
mixed() { dump "$1/hogumeter-old.sql.gz" 100; dump "$1/hogumeter-now.sql.gz"; }
noise() { dump "$1/hogumeter-now.sql.gz"; touch "$1/backup.log" "$1/hogumeter-old.sql"; }
spaced() { dump "$1/hogu meter-now.sql.gz"; }

echo "── 차단되어야 함 (exit 1) ──"
check 1 "덤프가 하나도 없다 (cron이 한 번도 안 돌았다)" no_dumps
check 1 "최신 덤프가 30시간 전 (cron이 조용히 멈췄다)" stale
check 1 "덤프가 비어 있다 (잘린 cron 출력)" truncated
check 1 "gzip이 깨졌다" corrupt

echo "── 통과해야 함 (exit 0) — 오차단은 사람이 게이트를 끄게 만든다 ──"
check 0 "방금 만든 덤프" fresh
check 0 "오래된 것과 새 것이 섞이면 **새 것**을 본다" mixed
check 0 "*.sql.gz 아닌 파일은 무시한다 (backup.log · 압축 안 된 .sql)" noise
check 0 "파일명에 공백이 있어도 깨지지 않는다 (ls -t 파싱 금지)" spaced

echo "── 디렉토리 자체가 없으면 차단 (exit 1) ──"
set +e
bash "$CHECK" "$work/does-not-exist" 26 >"$work/out" 2>&1
got=$?
set -e
if [ "$got" -eq 1 ]; then printf '  PASS  exit=1  디렉토리 부재\n'; else printf '  FAIL  expected=1 got=%s\n' "$got"; fail=1; fi

echo
if [ "$fail" -eq 0 ]; then echo "ALL PASS"; else echo "SOME FAILED"; fi
exit "$fail"
