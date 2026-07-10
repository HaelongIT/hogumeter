#!/usr/bin/env bash
# REL-04 — 백업이 **최근에** 성공했는지 본다.
#
#   bash scripts/check-backup-freshness.sh [백업디렉토리] [최대나이(시간)]
#
# 왜 필요한가: `pre-deploy`는 cron 등록을 [필수]로 적어 두지만, **cron이 도는지 확인하는 법이
# 없었다.** cron은 조용히 실패한다 — docker가 안 떠 있거나, 디스크가 찼거나, PATH가 다르거나.
# 그러면 백업이 3일째 없어도 아무도 모르고, 그 사실은 **복구가 필요한 날** 처음 드러난다.
#
# "덤프 파일이 있다"로는 부족하다. 넷을 본다:
#   ① 디렉토리가 있고 ② 덤프가 하나라도 있고 ③ 최신 덤프가 정해진 나이보다 젊고
#   ④ 그게 비어 있지 않고 gzip 무결성을 통과한다 (잘린 cron 출력을 잡는다)
#
# 기본 26시간 = 일 1회 cron(03:10)에 두 시간 여유. cron에 걸어 실패 시 알림을 받거나,
# 배포 후 사람이 한 번 친다.

set -euo pipefail

root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
dir="${1:-$root/backups}"
max_age_hours="${2:-26}"

[ -d "$dir" ] || {
	echo "FAIL: 백업 디렉토리가 없다: $dir" >&2
	echo "  cron이 한 번도 돌지 않았거나 경로가 다르다. bash scripts/backup.sh" >&2
	exit 1
}

# 최신 덤프. `ls -t`를 파싱하지 않는다 — 공백·개행이 든 파일명에서 깨진다.
newest=$(find "$dir" -maxdepth 1 -type f -name '*.sql.gz' -printf '%T@ %p\n' 2>/dev/null |
	sort -rn | head -1 | cut -d' ' -f2- || true)
[ -n "$newest" ] || {
	echo "FAIL: $dir 에 덤프(*.sql.gz)가 하나도 없다" >&2
	exit 1
}

# 나이. `-mmin`은 분 단위 정수라 시간을 분으로 환산한다.
fresh=$(find "$newest" -mmin "-$((max_age_hours * 60))" -print -quit 2>/dev/null || true)
[ -n "$fresh" ] || {
	echo "FAIL: 최신 덤프가 ${max_age_hours}시간보다 오래됐다: $(basename "$newest")" >&2
	echo "  cron이 조용히 멈췄다. 이 사실은 복구가 필요한 날 처음 드러난다." >&2
	exit 1
}

# 잘린 cron 출력을 잡는다. `backup.sh`도 같은 검사를 하지만, 그 뒤에 무슨 일이 있었는지는 모른다.
[ -s "$newest" ] || {
	echo "FAIL: 최신 덤프가 비어 있다: $(basename "$newest")" >&2
	exit 1
}
gzip -t "$newest" 2>/dev/null || {
	echo "FAIL: 최신 덤프의 gzip 무결성 검사 실패: $(basename "$newest")" >&2
	echo "  검증되지 않은 백업은 백업이 아니다. bash scripts/restore-drill.sh \"$newest\"" >&2
	exit 1
}

echo "BACKUP OK: $(basename "$newest") ($(du -h "$newest" | cut -f1), ${max_age_hours}h 이내)"
