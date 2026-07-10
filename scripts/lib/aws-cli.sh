#!/usr/bin/env bash
# S3 호환 스토리지 호출 한 곳. `offsite-upload.sh`와 `check-offsite-freshness.sh`가 함께 쓴다.
#
# 왜 라이브러리인가: 이 docker 인자 목록을 두 벌 두면 **사본은 드리프트한다** — 한쪽에만
# `--endpoint-url`이 붙어 리허설(MinIO)은 통과하고 운영(실 AWS)은 죽는 식이다.
#
# aws-cli는 컨테이너로 실행한다 — 호스트에 설치하지 않는다(이식성, OPS-02).
#
# 환경변수:
#   BACKUP_S3_ENDPOINT     (선택) MinIO 등 S3 호환 엔드포인트. 비면 실 AWS.
#   OFFSITE_DOCKER_NETWORK (선택) aws-cli 컨테이너를 붙일 도커 네트워크(리허설용)
#   AWS_CLI_MOUNT          (선택) `-v` 인자. 파일을 올릴 때만 필요하다("host:/data").
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY / AWS_DEFAULT_REGION

aws_cli() {
	# MSYS_NO_PATHCONV: `-v host:/data`의 컨테이너 쪽 `/data`를 Git Bash가 변환하지 못하게 막는다.
	MSYS_NO_PATHCONV=1 docker run --rm \
		${OFFSITE_DOCKER_NETWORK:+--network "$OFFSITE_DOCKER_NETWORK"} \
		${AWS_CLI_MOUNT:+-v "$AWS_CLI_MOUNT"} \
		-e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
		-e AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-2}" \
		-e AWS_EC2_METADATA_DISABLED=true \
		-e AWS_S3_ADDRESSING_STYLE=path \
		amazon/aws-cli:2.35.19 \
		${BACKUP_S3_ENDPOINT:+--endpoint-url "$BACKUP_S3_ENDPOINT"} "$@"
}
