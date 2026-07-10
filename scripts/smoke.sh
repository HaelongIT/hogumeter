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
# 파이프라인 트리거를 촘촘히 — 스모크가 60초를 기다릴 수는 없다(운영 기본은 60000).
export CORE_PIPELINE_INTERVAL_MS=2000

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

echo "--- 0) OBS-04 헬스체크: compose가 healthy라고 말한다 ---"
# 헬스체크는 "정의했다"가 아니라 "실제로 healthy를 보고한다"까지 확인해야 한다.
# 명령이 이미지에 없거나(curl/wget 부재) 인증에 막히면 조용히 unhealthy로 굳는다.
for _ in $(seq 30); do
	statuses=$(compose ps --format '{{.Service}}:{{.Health}}')
	case "$statuses" in
	*core:healthy*) [ -n "$(echo "$statuses" | grep '^web:healthy')" ] && healthy=1 && break ;;
	esac
	sleep 2
done
[ "${healthy:-0}" = 1 ] || fail "core·web이 healthy가 되지 않았다: $(compose ps --format '{{.Service}}:{{.Health}}' | tr '\n' ' ')"
curl -fsS "${WEB}/healthz" | grep -q '^ok$' || fail "/healthz가 ok를 주지 않는다"

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
# 라벨로 variant를 집는다. `sed 's/.*"variantId"...'`의 greedy `.*`는 **마지막** 것을 집어
# "256GB를 노렸는데 512GB를 조회하는" 사고가 난다. 객체 경계로 쪼갠 뒤 라벨로 고른다.
variants_json=$(curl -fsS "${WEB}/api/v1/products/${product_id}/variants")
variant_of() {
	echo "$variants_json" | sed 's/{"variantId"/\n{"variantId"/g' |
		grep "\"label\":\"$1\"" | sed 's/[^0-9]*\([0-9]*\).*/\1/' | head -1
}
variant_256=$(variant_of 256GB)
# 딜은 256GB에만 붙는다(제목에 그 축값이 있으므로). 아래 조회·구매 단계는 **딜이 없어야** 하므로 512GB를 쓴다.
variant_id=$(variant_of 512GB)
[ -n "$variant_256" ] && [ -n "$variant_id" ] || fail "variant를 라벨로 찾지 못했다: $variants_json"
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

echo "--- 5-1b) 파이프라인 트리거: raw_deal_post -> deal_event -> 기준가 ---"
# 이 경로는 2026-07-10까지 **프로덕션에서 한 번도 실행된 적이 없었다** — @Scheduled가 없어
# 수집된 원문을 아무도 소비하지 않았다(docs/91 Q-27). collector 없이 계약 테이블에 직접 한 행을 넣어
# core의 스케줄러가 그걸 실제로 집어가는지 본다.
#
# 제목 '스모크 제품 256GB 특가' → 별칭 '스모크' substring 히트 → variant 축값 '256GB' 토큰 일치
# → Matcher CONFIRMED → deal_event 생성. (매칭이 깨지면 review_queue_item으로 빠져 tier가 NONE에 머문다.)
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "raw_deal_post 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
values ('ppomppu', 'smoke-1', 'https://example.invalid/1', '스모크 제품 256GB 특가', 999000, now(), 'ACTIVE');
SQL

# 스케줄러 주기는 2초. 넉넉히 기다리되 무한정은 아니다. 딜은 256GB variant에 붙는다.
for _ in $(seq 20); do
	bench=$(curl -fsS "${WEB}/api/v1/variants/${variant_256}/benchmark?periodMonths=6" || true)
	case "$bench" in
	*'"tier":"SPARSE"'*) ingested=1 && break ;;
	esac
	sleep 2
done
[ "${ingested:-0}" = 1 ] || fail "파이프라인이 원문을 소비하지 않았다(tier가 NONE에 머묾). 매칭이 CANDIDATE로 빠졌는지 review_queue_item을 볼 것. 마지막 응답: $bench"

# 표본이 1건이면 SPARSE — 통계 필드는 여전히 null이어야 한다(정직성 계약, BM-06 AC-3).
echo "$bench" | grep -q '"n":1' || fail "표본 수가 1이 아니다: $bench"
echo "$bench" | grep -q '"benchmarkPrice":null' || fail "SPARSE인데 기준가를 냈다: $bench"
echo "$bench" | grep -q '"cases":\[{' || fail "SPARSE인데 사례가 비었다: $bench"
echo "$bench" | grep -q '999000' || fail "적재한 가격이 사례에 없다: $bench"

# OBS-02: 조용히 도는 스케줄러는 아무것도 처리하지 않는 스케줄러와 구별되지 않는다.
# 매 틱 카운터를 남기고, 딜을 하나 만든 틱은 그렇게 말해야 한다.
tick=$(compose logs --no-log-prefix core 2>&1 | grep 'pipeline tick' | grep 'dealsCreated=1' | tail -1)
[ -n "$tick" ] || fail "파이프라인 틱 카운터에 dealsCreated=1이 없다"
echo "$tick" | grep -q 'merged=0' || fail "병합이 아닌데 merged가 0이 아니다: $tick"
echo "$tick" | grep -q 'pending=0' || fail "원문을 다 처리했는데 pending이 남았다: $tick"

echo "--- 5-2) 구매 기록(PUR) 왕복 — 쓰기 → 관찰 문맥 ---"
# 딜이 하나도 없는 variant를 샀다. 정답은 "활성 딜 없음 + 더 싼 기회 0건"이다.
purchase=$(mktemp)
cat >"$purchase" <<JSON
{"variantId": ${variant_id}, "demandAxisValue": null, "paidPrice": 899000,
 "purchasedAt": "2026-07-01T14:59:00Z", "observationDays": null, "linkedDealEventId": null}
JSON
created_purchase=$(curl -fsS -X POST "${WEB}/api/v1/purchases" \
	-H 'Content-Type: application/json' -d @"$purchase") || fail "구매 기록 POST 실패"
rm -f "$purchase"
echo "$created_purchase" | grep -q '"purchaseId"' || fail "purchaseId를 돌려주지 않는다: $created_purchase"

observations=$(curl -fsS "${WEB}/api/v1/variants/${variant_id}/purchases")
echo "$observations" | grep -q '"state":"OBSERVING"' || fail "구매가 OBSERVING이 아니다: $observations"
echo "$observations" | grep -q '"mode":"NO_ACTIVE_DEAL"' || fail "딜 0건인데 활성 딜이 있다고 한다"
echo "$observations" | grep -q '"cheaperChanceCount":0' || fail "놓친 기회가 0이 아니다"
echo "$observations" | grep -q '"paidPrice":899000' || fail "실지불가가 왕복하지 않는다"

echo "--- 6) collector는 opt-in 없이 네트워크를 만지지 않는다 (OBS-01 구조화 로그) ---"
# 로그는 JSON Lines다. 문장을 grep하지 말고 이벤트를 본다 — 문구는 바뀌어도 계약은 안 바뀐다.
collector_log=$(compose logs --no-log-prefix collector 2>&1 | grep '^{' | tail -1)
echo "$collector_log" | grep -q '"event":"refused"' || fail "collector가 refused 이벤트를 내지 않았다: $collector_log"
echo "$collector_log" | grep -q '"reason":"network_opt_in_missing"' || fail "정지 사유가 기록되지 않았다"

echo "--- 7) SEC-02 Basic Auth: 켜면 막고, 끄면 열린다 ---"
# 위 1~6은 auth 미설정(기본 off) 경로였다. 이제 켠 경로를 같은 이미지로 검증한다.
#
# 해시는 `htpasswd -nbm smoke smoke-pass` 산출물(apr1)이고, **평문 `smoke-pass`도 바로 아래
# `-u`에 그대로 있다.** 성공 경로(200)를 확인하려면 평문이 필요하고, 해시와 짝이 맞아야 한다.
# 이 자격증명은 여기서 띄우는 일회용 컨테이너에만 쓰이며 어디에도 배포되지 않는다 —
# 그래서 `.gitleaks.toml`이 이 문자열·이 파일·이 규칙에 한해(AND) 예외를 둔다.
# 다른 자격증명을 여기 넣으면 gitleaks가 잡는다.
image=$(compose config --images | grep -E 'web$' | head -1)
htpasswd='smoke:$apr1$HvjdDxij$LfiNPd.VUQvfyKaOeKNib0'
auth_cid=$(docker run -d -p "127.0.0.1:${AUTH_PORT:-54000}:80" -e WEB_BASIC_AUTH_HTPASSWD="$htpasswd" "$image")
sleep 2
auth_url="http://127.0.0.1:${AUTH_PORT:-54000}/"
code_no_creds=$(curl -s -o /dev/null -w '%{http_code}' "$auth_url")
code_with_creds=$(curl -s -o /dev/null -w '%{http_code}' -u smoke:smoke-pass "$auth_url")
# 헬스체크는 인증 뒤에 숨으면 안 된다 — 자격증명 없이도 200이어야 컨테이너가 healthy가 된다.
code_health=$(curl -s -o /dev/null -w '%{http_code}' "${auth_url}healthz")
docker rm -f "$auth_cid" >/dev/null
[ "$code_health" = 200 ] || fail "auth를 켜니 /healthz가 ${code_health}다 (헬스체크가 막힌다)"
[ "$code_no_creds" = 401 ] || fail "Basic Auth를 켰는데 인증 없이 ${code_no_creds}를 준다"
[ "$code_with_creds" = 200 ] || fail "올바른 자격증명인데 ${code_with_creds}를 준다"

echo
echo "SMOKE PASS: web -> nginx -> core -> postgres 왕복 + SEC-02 Basic Auth 확인"
