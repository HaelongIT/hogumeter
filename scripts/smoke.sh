#!/usr/bin/env bash
# 전체 스택 종단 스모크 — 브라우저가 실제로 걷는 길을 그대로 걷는다.
#
#   web(nginx) → /api 프록시 → core → Flyway·JPA → postgres
#
# 각 모듈 테스트가 GREEN이어도 이걸 통과한다는 보장은 없다. 실제로 nginx가
# `proxy_pass`의 업스트림을 기동 시점에 해석하다 죽은 적이 있고(docs/99), 그건 어떤
# 단위 테스트도 못 잡았다. **빌드 성공은 기동 성공이 아니다.**
#
# 격리 규율: 전용 프로젝트 이름(`hogumeter-smoke`)과 전용 포트를 쓴다. 개발용 스택·볼륨을
# 절대 건드리지 않는다 — 마지막의 `down -v`가 개발 DB를 지우면 안 되기 때문이다.
#
# 사용: bash scripts/smoke.sh

set -euo pipefail

PROJECT="hogumeter-smoke"
export DB_PASSWORD="${DB_PASSWORD:-smoke-only-not-a-secret}"
export POSTGRES_PORT="${POSTGRES_PORT:-55432}"
export CORE_PORT="${CORE_PORT:-58080}"
export WEB_PORT="${WEB_PORT:-53000}"
# 스모크에서 실 사이트를 긁지 않는다(정지조건). collector는 안내만 출력하고 종료한다.
export COLLECTOR_ALLOW_NETWORK=0

WEB="http://127.0.0.1:${WEB_PORT}"
compose() { docker compose -p "$PROJECT" "$@"; }

cleanup() {
	echo "--- 정리 (전용 프로젝트만) ---"
	compose down -v --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
	echo "FAIL: $*" >&2
	echo "--- core 로그 ---" >&2
	compose logs --tail 40 core >&2 || true
	exit 1
}

echo "--- 빌드 ---"
compose build --quiet

echo "--- 기동 ---"
compose up -d

echo "--- core 준비 대기 (최대 120초) ---"
for _ in $(seq 60); do
	if curl -fsS -o /dev/null "${WEB}/api/v1/products" 2>/dev/null; then
		ready=1
		break
	fi
	sleep 2
done
[ "${ready:-0}" = 1 ] || fail "core가 120초 안에 준비되지 않았다"

echo "--- 1) 정적 자산이 서빙된다 ---"
curl -fsS "${WEB}/" | grep -q '<div id="root">' || fail "index.html이 아니다"

echo "--- 2) SPA 폴백 (알 수 없는 경로 → index.html) ---"
[ "$(curl -s -o /dev/null -w '%{http_code}' "${WEB}/unknown/route")" = 200 ] || fail "SPA 폴백 실패"

echo "--- 3) nginx가 /api를 core로 넘긴다 (빈 목록) ---"
[ "$(curl -fsS "${WEB}/api/v1/products")" = "[]" ] || fail "빈 제품 목록이 아니다"

echo "--- 4) 등록 → postgres 왕복 → 목록 반영 (한글 UTF-8 포함) ---"
# 페이로드를 **파일로** 보낸다. Windows의 curl.exe는 argv의 한글을 cp949로 넘겨
# core가 `Invalid UTF-8 start byte 0xbd`로 400을 낸다. 파일이면 바이트가 그대로 간다.
payload=$(mktemp)
cat >"$payload" <<'JSON'
{"name":"스모크 제품","category":"test","demandAxisMode":"GROUPED",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB","512GB"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}},
             {"label":"512GB","priceAxisValues":{"용량":"512GB"}}],
 "aliases":["스모크"]}
JSON
created=$(curl -fsS -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' -d @"$payload") ||
	fail "등록 POST 실패"
rm -f "$payload"
echo "$created" | grep -q '"productId"' || fail "등록 응답에 productId가 없다: $created"

listed=$(curl -fsS "${WEB}/api/v1/products")
# 한글이 postgres를 왕복해 그대로 돌아와야 한다(인코딩 사고는 조용히 깨진다).
echo "$listed" | grep -q '스모크 제품' || fail "등록한 제품이 목록에 없다(한글 왕복 실패?)"
echo "$listed" | grep -q '용량' || fail "축 이름(한글)이 왕복하지 않는다"
echo "$listed" | grep -q '"variantId"' || fail "variantId가 노출되지 않는다"

echo "--- 5) variant 조회 (web이 기준가로 가려면 이게 있어야 한다) ---"
product_id=$(echo "$created" | sed 's/.*"productId"[: ]*\([0-9]*\).*/\1/')
[ "$(curl -fsS "${WEB}/api/v1/products/${product_id}/variants" | grep -o '"variantId"' | wc -l)" = 2 ] ||
	fail "variant 2개가 아니다"

echo "--- 5-1) 판단 화면이 부르는 조회 3종 (신호등·기준가·주기) ---"
# 방금 등록한 variant엔 딜이 하나도 없다. 그래서 정답은 "표본 없음"이다 —
# 이 경로가 조용히 0원·GREEN을 내면 화면이 거짓말을 한다.
variant_id=$(curl -fsS "${WEB}/api/v1/products/${product_id}/variants" |
	sed 's/.*"variantId"[: ]*\([0-9]*\).*/\1/')
bench=$(curl -fsS "${WEB}/api/v1/variants/${variant_id}/benchmark?periodMonths=6")
echo "$bench" | grep -q '"tier":"NONE"' || fail "딜 0건인데 tier가 NONE이 아니다: $bench"
echo "$bench" | grep -q '"n":0' || fail "표본 수가 0이 아니다: $bench"
curl -fsS "${WEB}/api/v1/variants/${variant_id}/signal" | grep -q '"color":"GRAY"' ||
	fail "표본 0인데 신호등이 GRAY가 아니다"
curl -fsS "${WEB}/api/v1/variants/${variant_id}/cadence" | grep -q '"guardMet":false' ||
	fail "발생 0인데 주기 가드가 통과했다"
# 없는 variant는 도메인 코드로 거절한다(web은 이 code를 그대로 보여준다).
curl -sS -o /dev/null -w '%{http_code}' "${WEB}/api/v1/variants/999999/benchmark?periodMonths=6" |
	grep -q '^404$' || fail "없는 variant인데 404가 아니다"

echo "--- 6) collector는 opt-in 없이 네트워크를 만지지 않는다 (OBS-01 구조화 로그) ---"
# 로그는 JSON Lines다. 문장을 grep하지 말고 이벤트를 본다 — 문구는 바뀌어도 계약은 안 바뀐다.
collector_log=$(compose logs --no-log-prefix collector 2>&1 | grep '^{' | tail -1)
echo "$collector_log" | grep -q '"event":"refused"' || fail "collector가 refused 이벤트를 내지 않았다: $collector_log"
echo "$collector_log" | grep -q '"reason":"network_opt_in_missing"' || fail "정지 사유가 기록되지 않았다"

echo "--- 7) SEC-02 Basic Auth: 켜면 막고, 끄면 열린다 ---"
# 위 1~6은 auth 미설정(기본 off) 경로였다. 이제 켠 경로를 같은 이미지로 검증한다.
# 해시는 `htpasswd -nbm smoke smoke-pass` 산출물(apr1). 평문 비밀번호는 어디에도 두지 않는다.
image=$(compose config --images | grep -E 'web$' | head -1)
htpasswd='smoke:$apr1$HvjdDxij$LfiNPd.VUQvfyKaOeKNib0'
auth_cid=$(docker run -d -p "127.0.0.1:${AUTH_PORT:-54000}:80" -e WEB_BASIC_AUTH_HTPASSWD="$htpasswd" "$image")
sleep 2
auth_url="http://127.0.0.1:${AUTH_PORT:-54000}/"
code_no_creds=$(curl -s -o /dev/null -w '%{http_code}' "$auth_url")
code_with_creds=$(curl -s -o /dev/null -w '%{http_code}' -u smoke:smoke-pass "$auth_url")
docker rm -f "$auth_cid" >/dev/null
[ "$code_no_creds" = 401 ] || fail "Basic Auth를 켰는데 인증 없이 ${code_no_creds}를 준다"
[ "$code_with_creds" = 200 ] || fail "올바른 자격증명인데 ${code_with_creds}를 준다"

echo
echo "SMOKE PASS: web -> nginx -> core -> postgres 왕복 + SEC-02 Basic Auth 확인"
