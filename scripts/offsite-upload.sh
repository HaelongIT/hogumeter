#!/usr/bin/env bash
# REL-04 오프사이트 사본 — 덤프 하나를 S3 호환 스토리지에 올리고 **거기 있는지 확인**한다.
#
#   bash scripts/offsite-upload.sh backups/hogumeter-20260709T031000Z.sql.gz
#
# 켜는 스위치는 `BACKUP_S3_BUCKET` 하나다. 없으면 아무것도 하지 않고 그 사실을 말한다 —
# "오프사이트가 있다"고 착각하는 것이 오프사이트가 없는 것보다 나쁘다.
#
# 환경변수:
#   BACKUP_S3_BUCKET    (필수) 예: hogumeter-backups
#   BACKUP_S3_PREFIX    (선택) 기본 "postgres"
#   BACKUP_S3_ENDPOINT  (선택) MinIO 등 S3 호환 엔드포인트. 비면 실 AWS.
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_DEFAULT_REGION
#   OFFSITE_DOCKER_NETWORK (선택) aws-cli 컨테이너를 붙일 도커 네트워크(리허설용)
#
# aws-cli는 컨테이너로 실행한다 — 호스트에 설치하지 않는다(이식성, OPS-02).

set -euo pipefail

file="${1:?업로드할 덤프 파일 경로가 필요합니다}"
[ -s "$file" ] || {
	echo "offsite: 빈 파일이거나 없습니다: $file" >&2
	exit 1
}

if [ -z "${BACKUP_S3_BUCKET:-}" ]; then
	echo "offsite: 미설정(BACKUP_S3_BUCKET) - 로컬 사본만 있습니다. 디스크가 죽으면 백업도 함께 죽습니다."
	exit 0
fi

prefix="${BACKUP_S3_PREFIX:-postgres}"
key="${prefix}/$(basename "$file")"
local_size=$(wc -c <"$file" | tr -d ' ')

# docker의 -v는 **호스트 OS의 경로**를 원한다. Git Bash의 `/tmp/...`는 그런 경로가 아니다.
# `pwd -W`가 MSYS 경로를 `C:/...`로 돌려준다(리눅스에선 -W가 없으므로 그냥 pwd).
host_dir=$(cd "$(dirname "$file")" && pwd)
case "$(uname -s)" in
MINGW* | MSYS*) host_dir=$(cd "$(dirname "$file")" && pwd -W) ;;
esac

aws() {
	# MSYS_NO_PATHCONV: `-v host:/data`의 컨테이너 쪽 `/data`를 Git Bash가 변환하지 못하게 막는다.
	MSYS_NO_PATHCONV=1 docker run --rm \
		${OFFSITE_DOCKER_NETWORK:+--network "$OFFSITE_DOCKER_NETWORK"} \
		-v "${host_dir}:/data" \
		-e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
		-e AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}" \
		-e AWS_EC2_METADATA_DISABLED=true \
		-e AWS_S3_ADDRESSING_STYLE=path \
		amazon/aws-cli:2.35.19 \
		${BACKUP_S3_ENDPOINT:+--endpoint-url "$BACKUP_S3_ENDPOINT"} "$@"
}

echo "offsite: s3://${BACKUP_S3_BUCKET}/${key} <- $(basename "$file") (${local_size} bytes)"
aws s3 cp "/data/$(basename "$file")" "s3://${BACKUP_S3_BUCKET}/${key}" --only-show-errors

# "업로드 명령이 성공했다"와 "객체가 거기 있다"는 다른 사건이다. 크기까지 대조한다.
remote_size=$(aws s3api head-object --bucket "$BACKUP_S3_BUCKET" --key "$key" --query ContentLength --output text |
	tr -d '\r')

[ "$remote_size" = "$local_size" ] || {
	echo "offsite: 크기 불일치 (로컬 ${local_size} != 원격 ${remote_size})" >&2
	exit 1
}
echo "offsite: 확인 완료 (${remote_size} bytes)"
