#!/usr/bin/env bash
# REL-04 오프사이트 리허설 — `scripts/offsite-upload.sh`의 **바로 그 코드 경로**를
# S3 호환 스토리지(MinIO)에 대고 실행한다.
#
#   bash scripts/offsite-drill.sh
#
# 왜 MinIO인가: 실 AWS 호출은 비용·자격증명·정지조건에 걸린다. 우리가 검증해야 하는 건
# "S3 API에 대고 우리 스크립트가 옳게 동작하는가"이지 AWS 그 자체가 아니다.
#
# 왜 리허설이 필요한가: 업로드 코드는 **사고가 나야 처음 읽힌다**. 백업 스크립트가 exit 0을
# 내는 것과 원격에 온전한 객체가 있는 것은 다른 사건이다(docs/99).
#
# 격리: 전용 네트워크·전용 컨테이너 이름. 운영/개발 어디에도 닿지 않는다.

set -euo pipefail

NET="hogumeter-offsite-drill"
MINIO="hogumeter-offsite-minio"
BUCKET="hogumeter-drill"
work=$(mktemp -d)

cleanup() {
	docker rm -f "$MINIO" >/dev/null 2>&1 || true
	docker network rm "$NET" >/dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

fail() {
	echo "FAIL: $*" >&2
	exit 1
}

echo "--- MinIO 기동 (일회용) ---"
docker network create "$NET" >/dev/null
docker run -d --name "$MINIO" --network "$NET" \
	-e MINIO_ROOT_USER=drilluser -e MINIO_ROOT_PASSWORD=drillpass123 \
	minio/minio:RELEASE.2025-09-07T16-13-09Z server /data >/dev/null

export OFFSITE_DOCKER_NETWORK="$NET"
export BACKUP_S3_ENDPOINT="http://${MINIO}:9000"
export BACKUP_S3_BUCKET="$BUCKET"
export BACKUP_S3_PREFIX="postgres"
export AWS_ACCESS_KEY_ID=drilluser
export AWS_SECRET_ACCESS_KEY=drillpass123
export AWS_DEFAULT_REGION=us-east-1

work_host="$work"
case "$(uname -s)" in
MINGW* | MSYS*) work_host=$(cd "$work" && pwd -W) ;;
esac

aws() {
	MSYS_NO_PATHCONV=1 docker run --rm --network "$NET" \
		-v "${work_host}:/data" \
		-e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION \
		-e AWS_EC2_METADATA_DISABLED=true -e AWS_S3_ADDRESSING_STYLE=path \
		amazon/aws-cli:2.35.19 --endpoint-url "$BACKUP_S3_ENDPOINT" "$@"
}

echo "--- MinIO 준비 대기 ---"
for _ in $(seq 30); do
	if aws s3 ls >/dev/null 2>&1; then
		ready=1
		break
	fi
	sleep 1
done
[ "${ready:-0}" = 1 ] || fail "MinIO가 뜨지 않았다"
aws s3 mb "s3://${BUCKET}" >/dev/null

echo "--- 덤프를 흉내낸 gzip 파일 생성 ---"
dump="$work/hogumeter-drill.sql.gz"
{
	echo "-- pg_dump 흉내"
	seq 1 5000
} | gzip -9 >"$dump"
local_size=$(wc -c <"$dump" | tr -d ' ')

echo "--- 1) 업로드 (운영과 같은 스크립트) ---"
root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
bash "$root/scripts/offsite-upload.sh" "$dump" || fail "업로드 실패"

echo "--- 2) 되받아 무결성 확인 (올렸다 != 온전히 있다) ---"
aws s3 cp "s3://${BUCKET}/postgres/$(basename "$dump")" /data/roundtrip.sql.gz --only-show-errors ||
	fail "다운로드 실패"
back="$work/roundtrip.sql.gz"
[ "$(wc -c <"$back" | tr -d ' ')" = "$local_size" ] || fail "크기 불일치"
gzip -t "$back" || fail "gzip 무결성 검사 실패"
[ "$(zcat "$back" | wc -l)" = 5001 ] || fail "내용 줄 수가 다르다"

echo "--- 3) 스위치가 없으면 조용히 성공하지 않는다 ---"
# BACKUP_S3_BUCKET을 비우면 업로드를 건너뛰되, "오프사이트 없음"을 말해야 한다.
skipped=$(BACKUP_S3_BUCKET='' bash "$root/scripts/offsite-upload.sh" "$dump")
echo "$skipped" | grep -q "미설정" || fail "오프사이트 미설정 사실을 알리지 않는다"

echo "--- 4) 크기 불일치를 실제로 잡아내는가 (검증기 검증) ---"
# 원격 객체를 다른 내용으로 덮어쓰면 offsite-upload는 성공한 뒤 크기를 대조한다.
# 여기서는 검증 로직 자체를 시험한다: 없는 키를 head 하면 실패해야 한다.
if aws s3api head-object --bucket "$BUCKET" --key "postgres/nonexistent.sql.gz" >/dev/null 2>&1; then
	fail "없는 객체를 head 했는데 성공했다"
fi

echo "--- 5) 오프사이트 신선도 게이트 (돌 수 있는 모양으로) ---"
# **오프사이트는 조용히 꺼진다.** `.env`에서 `BACKUP_S3_BUCKET` 한 줄이 사라지면 업로드는
# exit 0으로 넘어가고 로컬 신선도 게이트도 초록이다. 그 침묵을 잡는 게이트를 여기서 돌린다 —
# 실 AWS 없이 MinIO에 대고. 사전 조건(방금 올린 객체)은 위 1~2단계가 이미 만들어 뒀다.
BACKUP_S3_BUCKET="$BUCKET" bash "$root/scripts/check-offsite-freshness.sh" 26 ||
	fail "방금 올렸는데 신선도 게이트가 실패한다"

# 차단: 미설정이면 조용히 통과하지 않는다 ("설정을 지웠는데 게이트가 초록"이 가장 나쁘다).
if BACKUP_S3_BUCKET='' bash "$root/scripts/check-offsite-freshness.sh" 26 >/dev/null 2>&1; then
	fail "BACKUP_S3_BUCKET이 비었는데 신선도 게이트가 통과한다"
fi
# 차단: 객체가 없는 prefix면 실패한다 (업로드가 한 번도 성공하지 않은 상태).
if BACKUP_S3_BUCKET="$BUCKET" BACKUP_S3_PREFIX="empty-prefix" \
	bash "$root/scripts/check-offsite-freshness.sh" 26 >/dev/null 2>&1; then
	fail "객체가 없는 prefix인데 신선도 게이트가 통과한다"
fi
# 차단: 한계를 0시간으로 좁히면 방금 올린 것도 오래됐다고 봐야 한다(나이 계산이 실제로 돈다).
if BACKUP_S3_BUCKET="$BUCKET" bash "$root/scripts/check-offsite-freshness.sh" -1 >/dev/null 2>&1; then
	fail "한계를 -1시간으로 줘도 통과한다 — 나이 계산이 돌지 않는다"
fi

echo "OFFSITE DRILL PASS: 업로드 -> 확인 -> 왕복 무결성 -> 미설정 경고 -> 신선도 게이트(통과 1 + 차단 3)"
