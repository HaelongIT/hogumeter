#!/usr/bin/env bash
# REL-04 — **오프사이트** 사본이 최근에 올라갔는지 본다.
#
#   BACKUP_S3_BUCKET=... bash scripts/check-offsite-freshness.sh [최대나이(시간)]
#
# 왜 필요한가: 오프사이트는 **조용히 꺼진다.** `.env`에서 `BACKUP_S3_BUCKET` 한 줄이 사라지면
# `offsite-upload.sh`는 "미설정 - 로컬 사본만 있습니다"를 찍고 **exit 0**을 낸다. cron은 초록이고,
# `check-backup-freshness.sh`(로컬)도 초록이다. 그런데 디스크가 죽는 날 백업도 함께 죽는다.
#
# 그래서 이 게이트는 **미설정을 실패로 본다.** 오프사이트를 안 쓰기로 했다면 cron에서 이 점검을
# 빼면 된다 — "설정을 지웠는데 게이트가 초록"이 가장 나쁘다.
#
# 기본 26시간 = 일 1회 cron(03:10)에 두 시간 여유.

set -euo pipefail

max_age_hours="${1:-26}"
prefix="${BACKUP_S3_PREFIX:-postgres}"

[ -n "${BACKUP_S3_BUCKET:-}" ] || {
	echo "FAIL: BACKUP_S3_BUCKET이 비어 있다 — 오프사이트 사본이 없다" >&2
	echo "  디스크가 죽으면 백업도 함께 죽는다. 안 쓰기로 했다면 이 점검을 cron에서 빼라." >&2
	exit 1
}

# shellcheck source=scripts/lib/aws-cli.sh
. "$(dirname "$0")/lib/aws-cli.sh"

# 가장 최근 객체 하나. 없으면 aws가 `None`을 준다(빈 문자열이 아니다).
latest=$(aws_cli s3api list-objects-v2 \
	--bucket "$BACKUP_S3_BUCKET" --prefix "$prefix/" \
	--query 'sort_by(Contents,&LastModified)[-1].[LastModified,Key]' --output text 2>/dev/null |
	tr -d '\r' || true)

case "$latest" in
"" | None* | *None)
	echo "FAIL: s3://${BACKUP_S3_BUCKET}/${prefix}/ 에 객체가 하나도 없다" >&2
	echo "  업로드가 한 번도 성공하지 않았거나 prefix가 다르다." >&2
	exit 1
	;;
esac

modified=$(printf '%s\n' "$latest" | awk '{print $1}')
key=$(printf '%s\n' "$latest" | awk '{print $2}')

# `date -d`는 GNU coreutils. aws가 주는 ISO8601(`2026-07-10T07:56:07+00:00`)을 그대로 먹는다.
modified_epoch=$(date -u -d "$modified" +%s 2>/dev/null || true)
[ -n "$modified_epoch" ] || {
	echo "FAIL: LastModified를 해석하지 못했다: $modified" >&2
	exit 1
}
age_hours=$(( ( $(date -u +%s) - modified_epoch ) / 3600 ))

[ "$age_hours" -le "$max_age_hours" ] || {
	echo "FAIL: 최신 오프사이트 객체가 ${age_hours}시간 전이다(한계 ${max_age_hours}시간): $key" >&2
	echo "  업로드가 조용히 멈췄다 — 자격증명 만료·버킷 정책·BACKUP_S3_BUCKET 삭제를 볼 것." >&2
	exit 1
}

echo "OFFSITE OK: $key (${age_hours}h 전, 한계 ${max_age_hours}h)"
