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
curl -fsS "${WEB}/api/v1/health" | grep -q '"db":{"status":"UP"}' || fail "헬스가 db 컴포넌트를 보고하지 않는다"

echo "--- 0-1) OBS-04: DB만 죽었을 때 core는 살아서 '무엇이 죽었는지' 말한다 ---"
# Q-50 ②가 요구한 구분. 이 드릴 없이는 "503을 준다"가 단위 테스트의 주장으로만 존재한다 —
# 실 스택에서는 Hikari가 커넥션을 30초 붙들어 healthcheck timeout이 먼저 끊을 수도 있다.
# core를 직접 친다(healthcheck가 치는 그 경로). nginx를 거치면 502가 헬스를 가린다.
health_body=$(mktemp)
compose stop postgres >/dev/null 2>&1 || fail "postgres를 멈추지 못했다"

code=$(curl -s -o "$health_body" -w '%{http_code}' --max-time 20 "http://127.0.0.1:${CORE_PORT}/api/v1/health") ||
	fail "DB가 죽으니 core가 아예 응답하지 않는다 — liveness와 readiness가 붙어 있다"
[ "$code" = 503 ] || fail "DB가 죽었는데 헬스가 ${code}다 (기대: 503)"
grep -q '"status":"DOWN"' "$health_body" || fail "전체 상태가 DOWN이 아니다: $(cat "$health_body")"
grep -q '"db":{"status":"DOWN"' "$health_body" || fail "죽은 컴포넌트를 지목하지 않는다: $(cat "$health_body")"
# SEC-01: 헬스 응답은 인증 없이 노출된다. JDBC 예외 메시지에는 접속 URL·사용자명이 들어 있고,
# 드라이버에 따라 자격증명까지 담는다. `&&`로 잇지 않는다 — 매치 없음(exit 1)이 set -e를 밟는다.
if grep -qiE 'password|jdbc:' "$health_body" || grep -qF -e "$DB_PASSWORD" "$health_body"; then
	fail "헬스 응답이 접속 정보를 흘린다: $(cat "$health_body")"
fi
rm -f "$health_body"

compose start postgres >/dev/null 2>&1 || fail "postgres를 되살리지 못했다"
for _ in $(seq 30); do
	if curl -fsS -o /dev/null --max-time 10 "http://127.0.0.1:${CORE_PORT}/api/v1/health" 2>/dev/null; then
		recovered=1
		break
	fi
	sleep 2
done
[ "${recovered:-0}" = 1 ] || fail "DB가 돌아왔는데 core 헬스가 UP으로 복귀하지 않는다 (커넥션 풀이 굳었다)"

echo "--- 0-2) 데이터 영속: postgres를 재생성해도 살아남는다 (명명 볼륨) ---"
# `pre-deploy`는 "운영에서 `docker compose down -v` 금지"라고 경고하지만, 데이터가 정말
# **명명 볼륨**에 있는지는 아무것도 확인하지 않았다. `volumes:` 한 줄을 지우면 데이터는 컨테이너
# 안에 남고, 이미지 갱신 한 번에 사라진다. 그건 프로세스 밖의 계약이라 어떤 단위 테스트도 안 본다.
pg_cid=$(compose ps -q postgres)
[ -n "$pg_cid" ] || fail "postgres 컨테이너를 찾지 못했다"
pg_mounts=$(docker inspect -f '{{range .Mounts}}{{.Type}}:{{.Name}}:{{.Destination}} {{end}}' "$pg_cid")
case "$pg_mounts" in
*volume:*pgdata*:/var/lib/postgresql/data*) ;;
*) fail "postgres 데이터가 명명 볼륨(pgdata)에 있지 않다: $pg_mounts" ;;
esac

# 행 하나를 남기고 컨테이너를 **새로 만든다**. 계약 테이블을 쓰지 않는다 —
# raw_deal_post에 넣으면 파이프라인이 집어가 뒤 단계의 카운터(pending·dealsCreated)를 흔든다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "영속 확인용 테이블 생성 실패"
create table if not exists smoke_persistence (marker text not null);
insert into smoke_persistence values ('alive');
SQL

compose up -d --force-recreate --no-deps postgres >/dev/null 2>&1 || fail "postgres 재생성 실패"
for _ in $(seq 30); do
	compose ps --format '{{.Service}}:{{.Health}}' | grep -q '^postgres:healthy' && pg_back=1 && break
	sleep 2
done
[ "${pg_back:-0}" = 1 ] || fail "재생성한 postgres가 healthy가 되지 않았다"

marker=$(compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-c "select marker from smoke_persistence limit 1" 2>/dev/null | tr -d '\r' || true)
[ "$marker" = "alive" ] || fail "컨테이너 재생성으로 데이터가 사라졌다 (볼륨이 붙어 있지 않다). marker='$marker'"
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-c "drop table smoke_persistence" >/dev/null || fail "정리 실패"

# core의 커넥션 풀은 방금 끊겼다. 뒤 단계가 그 상태를 물려받지 않게 복귀를 기다린다.
for _ in $(seq 30); do
	if curl -fsS -o /dev/null --max-time 10 "http://127.0.0.1:${CORE_PORT}/api/v1/health" 2>/dev/null; then
		core_back=1
		break
	fi
	sleep 2
done
[ "${core_back:-0}" = 1 ] || fail "postgres 재생성 후 core가 UP으로 복귀하지 않는다"

echo "--- 0-3) 수명 정책: 상주 3종은 unless-stopped, collector는 유예 30초 ---"
# compose의 `restart:`·`stop_grace_period:`는 **사고가 나야** 효력이 드러난다. 그때까지는
# 아무도 틀렸는지 모른다. `restart: no`로 바뀌면 core가 한 번 죽고 영영 돌아오지 않는다.
for service in postgres core web; do
	service_cid=$(compose ps -aq "$service")
	[ -n "$service_cid" ] || fail "$service 컨테이너를 찾지 못했다"
	restart_policy=$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' "$service_cid")
	[ "$restart_policy" = "unless-stopped" ] ||
		fail "$service 의 재시작 정책이 unless-stopped가 아니다: '$restart_policy' — 죽으면 안 돌아온다"
done

# collector의 한 사이클엔 3사 HTTP 요청이 들어 있다. docker 기본 유예(10초)로는 짧아
# SIGKILL이 사이클을 찢는다. `stop_grace_period: 30s` → 컨테이너의 StopTimeout.
collector_grace=$(docker inspect -f '{{.Config.StopTimeout}}' "$(compose ps -aq collector)")
[ "$collector_grace" = "30" ] ||
	fail "collector 종료 유예가 30초가 아니다: '$collector_grace' (docker 기본 10초면 사이클이 찢긴다)"

echo "--- 0-4) SEC-02: 공개 포트는 전부 루프백에만 바인딩된다 ---"
# `"127.0.0.1:${CORE_PORT}:8080"`에서 접두사 하나가 빠지면 **인증 없는 core REST가 0.0.0.0에 열린다**
# (인증은 nginx에만 있다). postgres도 마찬가지다. compose 파일을 grep하는 것으로는 부족하다 —
# override·기본값이 실제 바인딩을 바꾼다. 런타임 객체를 본다.
for service in postgres core web; do
	service_cid=$(compose ps -q "$service")
	[ -n "$service_cid" ] || fail "$service 컨테이너를 찾지 못했다"
	port_binds=$(docker inspect \
		-f '{{range $p, $conf := .NetworkSettings.Ports}}{{range $conf}}{{$p}}={{.HostIp}} {{end}}{{end}}' \
		"$service_cid")
	[ -n "$port_binds" ] || fail "$service 에 공개 포트가 없다(스모크가 접근하려면 있어야 한다)"
	case "$port_binds" in
	*=0.0.0.0*) fail "$service 가 모든 인터페이스에 열려 있다: $port_binds (인증은 nginx에만 있다)" ;;
	*=::*) fail "$service 가 IPv6 전역에 열려 있다: $port_binds" ;;
	esac
	case "$port_binds" in
	*=127.0.0.1*) ;;
	*) fail "$service 의 바인딩이 루프백이 아니다: $port_binds" ;;
	esac
done

# 기본 스택은 auth를 끈 상태다. **그 사실이 로그에 남아야** 운영에서 "켰는지"를 확인할 수 있다.
# 산문이 아니라 마커를 본다 — 문구를 grep하는 테스트는 문구를 굳힌다(docs/99).
compose logs --no-log-prefix web 2>&1 | grep -q 'SEC-02 basic_auth=off' ||
	fail "web이 basic_auth 상태를 마커로 남기지 않는다 (기본은 off여야 한다)"

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

# Q-49: 서버측 검증 — 클라이언트 검증을 우회해(curl) 빈 이름을 치면 500(DB NOT NULL)이 아니라
# 400 + 도메인 코드가 와야 한다(web이 읽는 계약). 파일로 보내 argv 인코딩 사고를 피한다.
bad_reg=$(mktemp)
echo '{"name":"  ","category":"test","demandAxisMode":"GROUPED","axes":[],"variants":[{"label":"x","priceAxisValues":{}}],"aliases":[]}' >"$bad_reg"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "${WEB}/api/v1/products" \
	-H 'Content-Type: application/json' -d @"$bad_reg")" = 400 ] || fail "빈 이름 등록이 400이 아니다 (Q-49)"
curl -s -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' -d @"$bad_reg" |
	grep -q '"code":"REG_INVALID_PRODUCT"' || fail "등록 400 응답에 도메인 코드가 없다 (Q-49)"
rm -f "$bad_reg"

listed=$(curl -fsS "${WEB}/api/v1/products")
# 한글이 postgres를 왕복해 그대로 돌아와야 한다(인코딩 사고는 조용히 깨진다).
echo "$listed" | grep -q '스모크 제품' || fail "등록한 제품이 목록에 없다(한글 왕복 실패?)"
echo "$listed" | grep -q '용량' || fail "축 이름(한글)이 왕복하지 않는다"
echo "$listed" | grep -q '"variantId"' || fail "variantId가 노출되지 않는다"
# Q-66 ②: 축 정의가 유형과 함께 돌아온다. `product_axis`는 등록이 쓰기만 하고 아무도 읽지 않는
# 테이블이었다 — 수요축은 variant를 안 나눠 목록에 흔적이 없으니, 이게 없으면 확인할 길이 없다.
echo "$listed" | grep -q '"axes"' || fail "축 정의(axes)가 제품 목록에 없다 (Q-66 ②)"
echo "$listed" | grep -q '"axisType":"PRICE"' || fail "축 유형이 왕복하지 않는다 (Q-66 ②): $listed"

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
signal=$(curl -fsS "${WEB}/api/v1/variants/${variant_id}/signal")
echo "$signal" | grep -q '"color":"GRAY"' || fail "표본 0인데 신호등이 GRAY가 아니다: $signal"
# 계약 드리프트: web SignalPage가 읽는 필드가 전부 있는가(정본 = web/api/types.ts의 SignalView).
for field in color goodDealLineEstablished notes; do
	echo "$signal" | grep -q "\"${field}\":" ||
		fail "web SignalView가 기대하는 필드 '${field}'가 응답에 없다 (계약 드리프트): $signal"
done

cadence=$(curl -fsS "${WEB}/api/v1/variants/${variant_id}/cadence")
echo "$cadence" | grep -q '"guardMet":false' || fail "발생 0인데 주기 가드가 통과했다: $cadence"
for field in eventCount intervalMedianDays elapsedDays observedMonths guardMet; do
	echo "$cadence" | grep -q "\"${field}\":" ||
		fail "web CadenceView가 기대하는 필드 '${field}'가 응답에 없다 (계약 드리프트): $cadence"
done
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
# Q-46①: 사례 DealRef가 조건 태그를 싣는다(빈 배열이라도 키는 있어야 — web이 deal.conditions를 읽는다).
echo "$bench" | grep -q '"conditions":' || fail "DealRef.conditions가 응답에 없다 (Q-46① 계약 드리프트): $bench"

# **계약 드리프트 종단 검증**: web의 BenchmarkView가 기대하는 필드가 응답에 **전부** 있는가.
# core가 필드 이름을 바꾸면(goodDealLine → goodDealCutoff 등) 단위 테스트는 GREEN인데 화면은
# 조용히 undefined를 그린다. 부품별 GREEN은 모듈 경계 계약을 보장하지 않는다(CLAUDE.md).
# 목록의 정본은 web/src/api/types.ts의 BenchmarkView다 — 필드를 늘리면 여기도 늘린다.
for field in tier benchmarkPrice goodDealLine periodLowest latestDeal n m expandedToMonths currentPrice gap cases; do
	echo "$bench" | grep -q "\"${field}\":" ||
		fail "web BenchmarkView가 기대하는 필드 '${field}'가 응답에 없다 (계약 드리프트): $bench"
done

# OBS-02: 조용히 도는 스케줄러는 아무것도 처리하지 않는 스케줄러와 구별되지 않는다.
# 매 틱 카운터를 남기고, 딜을 하나 만든 틱은 그렇게 말해야 한다.
tick=$(compose logs --no-log-prefix core 2>&1 | grep 'pipeline tick' | grep 'dealsCreated=1' | tail -1)
[ -n "$tick" ] || fail "파이프라인 틱 카운터에 dealsCreated=1이 없다"
echo "$tick" | grep -q 'merged=0' || fail "병합이 아닌데 merged가 0이 아니다: $tick"
echo "$tick" | grep -q 'pending=0' || fail "원문을 다 처리했는데 pending이 남았다: $tick"
# Q-57 ②③: 매칭 tier 카운터가 종단 로그에 도달한다. 이 틱엔 confirmed 원문 1건뿐이라 나머지는 0이다.
# (스냅샷 차이로는 못 세는 값 — 유스케이스가 직접 세어 리포트로 넘긴다. firstAlertsSent 값은 단위·통합 테스트가 잠근다.)
echo "$tick" | grep -q 'matched\[confirmed=1 candidate=0 unknown=0 rejected=0 skippedNoPrice=0\]' ||
	fail "매칭 tier 카운터가 틱 로그에 없다 (Q-57 계약 드리프트): $tick"
# Q-57: 후속 알림 발송 수도 종단 로그에 실린다(첫 알림만 세고 후속을 버리면 절반 카운터). 이 틱엔 후속 0.
echo "$tick" | grep -q 'followUpsSent\[priceChanged=0 ended=0\]' ||
	fail "후속 알림 카운터가 틱 로그에 없다 (Q-57): $tick"
# Q-56: 단계 실패 수도 실린다 — 건강한 틱은 0이라, 이게 비-0이면 "도는 척" 틱이 로그 한 줄에 보인다.
echo "$tick" | grep -q 'stepsFailed=0' || fail "단계 실패 카운터가 틱 로그에 없거나 0이 아니다 (Q-56): $tick"

# OBS-01: core 로그도 구조화(JSON)여야 한다. collector는 이미 JSON Lines다 —
# 두 컨테이너의 로그가 같은 모양이어야 한 곳에서 읽는다. 형식은 조용히 되돌아가므로 매번 본다.
# ECS는 필드를 중첩한다: {"ecs":{"version":"8.11"}}, {"log":{"level":"INFO", ...}}
echo "$tick" | grep -q '^{' || fail "core 로그가 JSON이 아니다(첫 글자가 { 가 아님): $tick"
echo "$tick" | grep -q '"ecs":{"version"' || fail "core 로그에 ECS 필드가 없다: $tick"
echo "$tick" | grep -q '"log":{"level":"INFO"' || fail "core 로그에 구조화된 레벨이 없다: $tick"
echo "$tick" | grep -q '"@timestamp"' || fail "core 로그에 타임스탬프가 없다: $tick"

echo "--- 5-1c) 가격 변경 재처리: raw 업서트 -> deal_event.price_last (BM-01 AC-2) ---"
# 수집기 재폴링을 흉내낸다 — 같은 원문의 가격이 내렸다. 이미 링크된 원문이라 ingest는 다시 안 읽는다.
# 그래서 별개 경로(ReprocessDealPricesUseCase)가 필요하다(docs/91 Q-27 ①).
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "raw_deal_post 가격 갱신 실패"
update raw_deal_post set headline_price = 899000, captured_at = now() where site='ppomppu' and post_id='smoke-1';
SQL

deal_price() {
	compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
		-c "select price_first || '/' || price_min || '/' || price_last from deal_event limit 1" | tr -d '\r'
}
for _ in $(seq 20); do
	prices=$(deal_price)
	[ "$prices" = "999000/899000/899000" ] && refreshed=1 && break
	sleep 2
done
# priceFirst는 불변(기준가 분포가 그 위에 선다) / priceMin은 지나간 기회 / priceLast는 "지금"
[ "${refreshed:-0}" = 1 ] || fail "가격 변경이 deal_event에 반영되지 않았다 (first/min/last=$prices)"

echo "--- 5-1d) 알림 정책 쓰기 → 새 딜 → intensity=TARGET (REG-03, 확정본 §107) ---"
# `alert_policy`는 `EvaluateAlertOnDealUseCase`가 **읽지만** 프로덕션에 writer가 없었다.
# 즉 "OR [사용자 목표가 이하]" 트리거는 발화할 수 없었고, 테스트만 손수 행을 넣어 GREEN이었다.
#
# 왜 intensity=TARGET이 증거인가: 표본 1건이면 tier=SPARSE라 정책이 없어도 GOOD 알림은 나간다.
# 목표가 우선순위가 GOOD보다 높으므로(§103), `intensity=TARGET`은 **정책 행을 읽었다는 뜻**이다.
# 별칭이 '스모크'와 겹치지 않는 새 제품을 쓴다 — 겹치면 두 제품에 매칭돼 미상 큐로 빠진다.
alert_payload=$(mktemp)
cat >"$alert_payload" <<'JSON'
{"name":"알림테스트 제품","category":"test","demandAxisMode":"GROUPED",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}}],
 "aliases":["알림테스트"]}
JSON
alert_product=$(curl -fsS -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' \
	-d @"$alert_payload") || fail "알림용 제품 등록 실패"
rm -f "$alert_payload"
alert_pid=$(echo "$alert_product" | sed 's/.*"productId"[: ]*\([0-9]*\).*/\1/')
alert_variants=$(curl -fsS "${WEB}/api/v1/products/${alert_pid}/variants") || fail "알림용 variant 조회 실패"
alert_vid=$(echo "$alert_variants" | sed 's/[^0-9]*\([0-9]*\).*/\1/')
[ -n "$alert_vid" ] || fail "알림용 variant를 찾지 못했다: $alert_variants"

# 미설정은 404가 아니라 configured:false다 — "정책 없음"과 "variant 없음"은 다른 사건이다.
unset_policy=$(curl -fsS "${WEB}/api/v1/variants/${alert_vid}/alert-policy")
echo "$unset_policy" | grep -q '"configured":false' || fail "미설정 정책이 configured:false를 내지 않는다"
# Q-48 ①: K는 미설정이라도 **숫자로** 온다 — 기본값의 정본이 core 상수 하나라 사본이 안 생긴다.
# (기간 P는 그 정본이 없어 여전히 부재다 — Q-48 ②.)
echo "$unset_policy" | grep -q '"kDisplay":5' ||
	fail "미설정 정책이 기본 K를 숫자로 내지 않는다 (Q-48 ①): $unset_policy"
# Q-28: 제외 키워드는 미설정이라도 **빈 배열로** 온다(null이 아님 — core가 List.of()를 낸다).
echo "$unset_policy" | grep -q '"excludeKeywords":\[\]' ||
	fail "미설정 정책이 빈 제외 키워드 배열을 내지 않는다 (Q-28): $unset_policy"
[ "$(curl -s -o /dev/null -w '%{http_code}' "${WEB}/api/v1/variants/999999/alert-policy")" = 404 ] ||
	fail "없는 variant의 정책 조회가 404가 아니다"

policy=$(mktemp)
echo '{"targetPrice":1000000,"periodMonths":6,"kDisplay":8,"excludeKeywords":["리퍼","벌크"]}' >"$policy"
curl -fsS -X PUT "${WEB}/api/v1/variants/${alert_vid}/alert-policy" \
	-H 'Content-Type: application/json' -d @"$policy" | grep -q '"targetPrice":1000000' ||
	fail "정책 PUT이 저장값을 되돌려주지 않는다"
# 사용자 손잡이는 **왕복해야** 손잡이다 — 저장한 K가 그대로 돌아오지 않으면 조용히 기본값이 된다.
saved_policy=$(curl -fsS "${WEB}/api/v1/variants/${alert_vid}/alert-policy")
echo "$saved_policy" | grep -q '"kDisplay":8' || fail "저장한 K가 왕복하지 않는다 (Q-48 ①)"
# Q-28: 제외 키워드도 왕복해야 손잡이다 — text[] 컬럼이 PUT→저장→GET 종단으로 살아 있는지 본다.
echo "$saved_policy" | grep -q '"excludeKeywords":\["리퍼","벌크"\]' ||
	fail "저장한 제외 키워드가 왕복하지 않는다 (Q-28): $saved_policy"
# 범위 밖(3~10)은 DB CHECK로 500이 아니라 도메인 코드 400이어야 한다.
echo '{"targetPrice":1000000,"periodMonths":6,"kDisplay":99}' >"$policy"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "${WEB}/api/v1/variants/${alert_vid}/alert-policy" \
	-H 'Content-Type: application/json' -d @"$policy")" = 400 ] || fail "범위 밖 K가 400이 아니다"
# 잘못된 입력이 500이 아니라 400 + 도메인 코드로 거절되는지 (curl로 직접 치는 경우, Q-49의 교훈)
echo '{"targetPrice":0,"periodMonths":6}' >"$policy"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X PUT "${WEB}/api/v1/variants/${alert_vid}/alert-policy" \
	-H 'Content-Type: application/json' -d @"$policy")" = 400 ] || fail "목표가 0원이 400이 아니다"
rm -f "$policy"

# 목표가(1,000,000) 아래의 딜을 새로 넣는다. 알림 판정은 딜 생성 시점에 돈다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "알림용 raw_deal_post 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
values ('ppomppu', 'smoke-alert', 'https://example.invalid/2', '알림테스트 256GB 초특가', 950000, now(), 'ACTIVE');
SQL

# `|| true`가 필요하다: `set -o pipefail` + `set -e`에서 첫 회차의 grep 실패(아직 알림 없음)가
# 파이프라인 상태로 올라와 스크립트를 **아무 메시지도 없이** 죽인다. 재시도 루프의 grep은 실패가 정상이다.
for _ in $(seq 20); do
	alert_log=$(compose logs --no-log-prefix core 2>&1 | grep 'STUB alert' | grep 'intensity=TARGET' | tail -1 || true)
	[ -n "$alert_log" ] && alerted=1 && break
	sleep 2
done
[ "${alerted:-0}" = 1 ] || fail "목표가를 저장했는데 TARGET 알림이 발화하지 않았다 (정책이 판정에 닿지 않는다). 최근 알림: $(compose logs --no-log-prefix core 2>&1 | grep 'STUB alert' | tail -3 || true)"
echo "$alert_log" | grep -q 'price=950000' || fail "알림이 딜 가격을 싣지 않았다: $alert_log"

echo "--- 5-1i) 수요축 분리(Q-66 ①): 값별로 기준가가 갈리고, 값 미상 딜은 빠진다 ---"
# 확정본 §40·41. SPLIT은 V1부터 저장만 되고 아무 동작도 바꾸지 못했다 — 모든 색이 한 분포에 섞였다.
split_payload=$(mktemp)
cat >"$split_payload" <<'JSON'
{"name":"분리 제품","category":"test","demandAxisMode":"SPLIT",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB"]},
         {"axisType":"DEMAND","name":"색상","allowedValues":["블랙","화이트"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}}],
 "aliases":["분리제품"]}
JSON
split_created=$(curl -fsS -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' -d @"$split_payload") ||
	fail "분리 제품 등록 실패"
rm -f "$split_payload"
split_pid=$(echo "$split_created" | sed 's/.*"productId"[: ]*\([0-9]*\).*/\1/')
split_vid=$(curl -fsS "${WEB}/api/v1/products/${split_pid}/variants" |
	sed 's/.*"variantId"[: ]*\([0-9]*\).*/\1/')
[ -n "$split_vid" ] || fail "분리 제품 variant를 찾지 못했다"

# 블랙 5건(기준가를 말하려면 K=5를 넘겨야 한다) + 화이트 1건(훨씬 비쌈) + 색 미상 1건.
# 가격 간격은 **병합 허용폭(±2%)보다 넓게** 30,000원씩 둔다 — 좁으면 BM-04가 한 딜로 합쳐 n이 1이 된다.
# 미상은 750,000: 이상치 하한(IQR) 밖이 아니라서 "이상치라 빠졌다"가 아니라 **미상이라 빠졌다**를 시험한다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "분리용 raw_deal_post 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status) values
 ('ppomppu','sp1','https://example.invalid/sp1','분리 제품 256GB 블랙 특가', 800000, now(), 'ACTIVE'),
 ('ppomppu','sp2','https://example.invalid/sp2','분리 제품 256GB 블랙 특가', 830000, now(), 'ACTIVE'),
 ('ppomppu','sp3','https://example.invalid/sp3','분리 제품 256GB 블랙 특가', 860000, now(), 'ACTIVE'),
 ('ppomppu','sp4','https://example.invalid/sp4','분리 제품 256GB 블랙 특가', 890000, now(), 'ACTIVE'),
 ('ppomppu','sp5','https://example.invalid/sp5','분리 제품 256GB 블랙 특가', 920000, now(), 'ACTIVE'),
 ('ppomppu','sp6','https://example.invalid/sp6','분리 제품 256GB 화이트 특가', 990000, now(), 'ACTIVE'),
 ('ppomppu','sp7','https://example.invalid/sp7','분리 제품 256GB 특가', 750000, now(), 'ACTIVE');
SQL

for _ in $(seq 24); do
	black=$(curl -fsS "${WEB}/api/v1/variants/${split_vid}/benchmark?periodMonths=6&demandAxisValue=%EB%B8%94%EB%9E%99" || true)
	echo "$black" | grep -q '"n":5' && break
	sleep 2
done
# 화이트가 섞였다면 6, 미상까지 섞였다면 7이다. median도 함께 본다 — 섞이면 860,000이 아니게 된다.
echo "$black" | grep -q '"n":5' ||
	fail "블랙 표본이 5가 아니다(화이트·미상이 섞였거나 아직 미수집): $black"
echo "$black" | grep -q '"benchmarkPrice":860000' ||
	fail "블랙 median이 860,000이 아니다(다른 색이 섞였다): $black"
# 값을 안 주면 400 — 전체 딜로 하나의 기준가를 내면 그게 곧 묶음의 거짓말이다(§41).
[ "$(curl -s -o /dev/null -w '%{http_code}' \
	"${WEB}/api/v1/variants/${split_vid}/benchmark?periodMonths=6")" = 400 ] ||
	fail "분리 제품인데 수요축 값 없이 기준가를 내준다 (묶음의 거짓말, Q-66 ①)"

# Q-66 ③: 구매도 분리면 값이 필수다. 없으면 400(성적을 어느 색 분포에 대고 낼지 알 수 없다).
buy=$(mktemp)
echo "{\"variantId\":${split_vid},\"paidPrice\":900000,\"purchasedAt\":\"2026-06-15T23:59:00+09:00\"}" >"$buy"
[ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "${WEB}/api/v1/purchases" \
	-H 'Content-Type: application/json' -d @"$buy")" = 400 ] ||
	fail "분리 제품인데 수요축 값 없이 구매가 기록된다 (Q-66 ③)"
# 값을 주면 그 색 분포에 대고 성적을 낸다 — 블랙 median 860,000 대비 900,000이면 +40,000.
echo "{\"variantId\":${split_vid},\"demandAxisValue\":\"블랙\",\"paidPrice\":900000,\"purchasedAt\":\"2026-06-15T23:59:00+09:00\"}" >"$buy"
curl -fsS -X POST "${WEB}/api/v1/purchases" -H 'Content-Type: application/json' -d @"$buy" |
	grep -q '"purchaseId"' || fail "분리 제품 구매(값 지정)가 기록되지 않는다 (Q-66 ③)"
rm -f "$buy"

# Q-66 ① E: 색을 못 읽은 딜(sp7 "특가")은 분포에서 빠질 뿐 아니라 **승격 큐(DEMAND_UNKNOWN)에 뜬다**.
# 빠지기만 하고 사람이 못 보면 유실이다(§41).
curl -fsS "${WEB}/api/v1/review-queue" | grep -q '"type":"DEMAND_UNKNOWN"' ||
	fail "색 미상 딜이 승격 큐에 뜨지 않는다 (Q-66 ① E)"

echo "--- 5-1e) 미상 큐: 매칭 실패 원문이 사람에게 보인다 + 중복 재처리 실측 (Q-27 ④) ---"
# `review_queue_item`은 2026-07-10까지 **쓰이기만 하고 아무도 읽지 않았다.** 매칭이 무엇을
# 놓치는지 볼 방법이 없었다 — 놓침을 허용하는 시스템에서 놓친 걸 못 보면 그건 유실이다.
#
# 별칭('스모크')은 맞는데 **축값(256GB/512GB)이 제목에 없다** → Matcher가 variant를 못 고른다(UNKNOWN)
# → deal_event 없이 review_queue_item만 생긴다. (아무 토큰도 안 겹치는 제목은 REJECTED로 그냥 버려진다.)
unknown_title='스모크 제품 1테라 특가'
deals_before=$(compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-c "select count(*) from deal_event" | tr -d '\r')
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "미상 원문 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
values ('ruliweb', 'smoke-unknown', 'https://example.invalid/unknown', '스모크 제품 1테라 특가', 111000, now(), 'ACTIVE');
SQL

for _ in $(seq 20); do
	queue=$(curl -fsS "${WEB}/api/v1/review-queue" || true)
	case "$queue" in
	*"$unknown_title"*) queued=1 && break ;;
	esac
	sleep 2
done
[ "${queued:-0}" = 1 ] || fail "매칭 실패 원문이 미상 큐에 뜨지 않는다: $queue"
echo "$queue" | grep -q '"type":"UNCLASSIFIED"' || fail "유형이 UNCLASSIFIED가 아니다: $queue"
# 후보를 id로 주면 사람이 못 읽는다. 별칭이 '스모크'에 걸렸으니 그 제품 이름이 나와야 한다.
echo "$queue" | grep -q '"candidateProducts":\["스모크 제품"\]' ||
	fail "후보 제품을 이름으로 풀지 않았다(id 그대로거나 비었다): $queue"
# 판단은 사람이 한다 — 시스템은 근거와 원문 링크만 모아준다(절대 원칙 2·6).
echo "$queue" | grep -q 'https://example.invalid/unknown' || fail "원문 링크를 잇지 못했다: $queue"

# 딜은 생기지 않아야 한다 — 미상은 기준가 표본에 들어가면 안 된다(정직성).
deals_after=$(compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-c "select count(*) from deal_event" | tr -d '\r')
[ "$deals_before" = "$deals_after" ] || fail "미상 원문이 deal_event를 만들었다 ($deals_before -> $deals_after)"

# ✅ Q-27 ④ 해소 검증: `findUnprocessed()`는 deal_event_source 링크가 없는 원문을 미처리로 본다.
# 미상 원문은 링크를 만들지 않아 **매 틱 다시 스캔된다**. 예전엔 그때마다 새 행이 쌓였다(하루 1,440행).
# 이제 같은 근거(dedup_key)를 **한 행에 접고** 재적재를 occurrences로 센다 — DB엔 정확히 1행, occurrences만
# 는다. 여러 틱을 기다려 재처리가 세지는지 본다(조용히 지우면 결함이 사라진 것처럼 보인다).
rows=0
occ=0
for _ in $(seq 20); do
	rows=$(compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
		-c "select count(*) from review_queue_item where payload->>'title' = '${unknown_title}'" | tr -d '\r')
	occ=$(compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
		-c "select coalesce(max(occurrences),0) from review_queue_item where payload->>'title' = '${unknown_title}'" | tr -d '\r')
	{ [ "$rows" = 1 ] && [ "${occ:-0}" -ge 2 ]; } && break
	sleep 2
done
[ "$rows" = 1 ] || fail "미상 원문이 한 행으로 접히지 않았다 (${rows}행) — Q-27 ④ dedup 회귀"
[ "${occ:-0}" -ge 2 ] || fail "재처리가 occurrences로 세지지 않는다 (occ=${occ}) — 매 틱 다시 스캔되면 늘어야 한다"

# 조회도 그 한 행을 occurrences로 드러낸다. `grep -c`는 **줄 수**라 한 줄 JSON에선 늘 1 — `grep -o | wc -l`로 등장 횟수를 센다.
queue=$(curl -fsS "${WEB}/api/v1/review-queue")
[ "$(echo "$queue" | grep -o "$unknown_title" | wc -l)" = 1 ] ||
	fail "화면이 같은 항목을 여러 번 그린다 (DB ${rows}행)"
echo "$queue" | grep -qE '"occurrences":[2-9][0-9]*' ||
	fail "재처리를 occurrences로 드러내지 않는다 (조용히 지우면 결함이 사라진 것처럼 보인다): $queue"

echo "--- 5-1f) 이상치(BM-05): 분포 하단 딜이 큐에 뜨고 **대상을 지목한다** ---"
# 🔥 경로는 종단으로 한 번도 실행된 적이 없었다. 분포가 5건 이상이어야 판정이 돈다
# (`OUTLIER_MIN_DISTRIBUTION`). 병합(±2%, 48h)에 먹히지 않게 가격을 벌려 심는다.
outlier_payload=$(mktemp)
cat >"$outlier_payload" <<'JSON'
{"name":"이상치테스트 제품","category":"test","demandAxisMode":"GROUPED",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}}],
 "aliases":["이상치테스트"]}
JSON
outlier_product=$(curl -fsS -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' \
	-d @"$outlier_payload") || fail "이상치용 제품 등록 실패"
rm -f "$outlier_payload"
outlier_pid=$(echo "$outlier_product" | sed 's/.*"productId"[: ]*\([0-9]*\).*/\1/')
outlier_variants=$(curl -fsS "${WEB}/api/v1/products/${outlier_pid}/variants") || fail "이상치용 variant 조회 실패"
outlier_vid=$(echo "$outlier_variants" | sed 's/[^0-9]*\([0-9]*\).*/\1/')

compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "이상치용 분포 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status) values
 ('ppomppu','out-1','https://example.invalid/o1','이상치테스트 256GB 특가',  900000, now(), 'ACTIVE'),
 ('ppomppu','out-2','https://example.invalid/o2','이상치테스트 256GB 특가',  950000, now(), 'ACTIVE'),
 ('ppomppu','out-3','https://example.invalid/o3','이상치테스트 256GB 특가', 1000000, now(), 'ACTIVE'),
 ('ppomppu','out-4','https://example.invalid/o4','이상치테스트 256GB 특가', 1050000, now(), 'ACTIVE'),
 ('ppomppu','out-5','https://example.invalid/o5','이상치테스트 256GB 특가', 1100000, now(), 'ACTIVE'),
 ('ppomppu','out-6','https://example.invalid/o6','이상치테스트 256GB 특가', 1150000, now(), 'ACTIVE');
SQL

# 분포가 6건이 될 때까지 기다린다(딜이 병합되면 n이 줄어 판정이 안 돈다).
for _ in $(seq 20); do
	bench=$(curl -fsS "${WEB}/api/v1/variants/${outlier_vid}/benchmark?periodMonths=6" || true)
	case "$bench" in
	*'"n":6'*) distributed=1 && break ;;
	esac
	sleep 2
done
[ "${distributed:-0}" = 1 ] || fail "분포가 6건이 되지 않았다(병합에 먹혔다?): $bench"

# **SUFFICIENT tier + 기준가(median) 산출은 종단으로 한 번도 검증된 적이 없었다** — 기준가 엔진의
# 최종 산출물인데도. n=6(≥ K_display 5)이면 tier=SUFFICIENT, benchmarkPrice=median이어야 한다.
# 분포 900/950/1000/1050/1100/1150(×1000)의 R-7 median: h=(6-1)×0.5=2.5 → 1000000 + 0.5×50000 = 1,025,000.
# **정확한 값**을 단언해 median이 실제로 계산됨을 증명한다(null 아님만으론 "0을 냈다"를 못 잡는다).
echo "$bench" | grep -q '"tier":"SUFFICIENT"' || fail "n=6인데 tier가 SUFFICIENT가 아니다: $bench"
echo "$bench" | grep -q '"benchmarkPrice":1025000' ||
	fail "기준가 median이 1,025,000이 아니다(R-7 산출이 안 돌았다): $bench"

# Tukey 하한: Q1(925,000) − 1.5×IQR(150,000) = 700,000. 300,000은 그보다 한참 아래다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "이상치 딜 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
values ('ppomppu','out-low','https://example.invalid/olow','이상치테스트 256GB 특가', 300000, now(), 'ACTIVE');
SQL

for _ in $(seq 20); do
	queue=$(curl -fsS "${WEB}/api/v1/review-queue" || true)
	case "$queue" in
	*'"type":"OUTLIER_LOWER"'*) outliered=1 && break ;;
	esac
	sleep 2
done
[ "${outliered:-0}" = 1 ] || fail "분포 하단 딜이 이상치 큐에 뜨지 않았다(BM-05가 안 돌았다): $queue"
# "300,000원"만 보고는 아무것도 결정할 수 없다 — **무엇의** 이상치인지 지목해야 한다.
echo "$queue" | grep -q '"subject":"이상치테스트 제품 — 256GB"' ||
	fail "이상치가 대상(제품 — variant)을 지목하지 않는다: $queue"
echo "$queue" | grep -q 'https://example.invalid/olow' || fail "이상치의 원문 링크를 잇지 못했다: $queue"
# 이상치는 기준가 표본에서 빠진다(정직성) — n은 6 그대로여야 한다.
curl -fsS "${WEB}/api/v1/variants/${outlier_vid}/benchmark?periodMonths=6" | grep -q '"n":6' ||
	fail "이상치가 기준가 표본에 섞였다"

# 조건 없는 이상치는 빈 배열이다 — 없는 이유를 지어내지 않는다.
echo "$queue" | grep -q '"conditions":\[\]' || fail "조건 없는 이상치가 빈 배열을 내지 않는다: $queue"

# **왜 싸 보이는가.** 이미 큐에 뜬 이상치의 원문에 조건 태그가 붙으면, 큐가 그 이유를 말한다.
#
# 새 저가 딜을 심지 않는다: 310,000원짜리 두 번째 저가 딜을 넣으면 **Q1이 끌려 내려가 둘 다
# 이상치가 아니게 된다**(2026-07-10 실측: 둘 다 `outlier_flag=NONE`). 임계를 넘기는 시나리오는
# 옆의 다른 임계를 피해야 한다(docs/99). 실제 운영 흐름도 이쪽이다 — 이상치가 먼저 큐에 뜨고,
# 다음 폴링이 원문의 조건 태그를 실어 온다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "조건 태그 업서트 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status, raw)
values ('ppomppu','out-low','https://example.invalid/olow','이상치테스트 256GB 특가', 300000, now(), 'ACTIVE',
        '{"_derived":{"applied_conditions":["카할","배송비미상"]}}'::jsonb)
on conflict (site, post_id) do update set raw = excluded.raw, captured_at = excluded.captured_at;
SQL

for _ in $(seq 20); do
	queue=$(curl -fsS "${WEB}/api/v1/review-queue" || true)
	case "$queue" in
	*'"conditions":["배송비미상","카할"]'*) conditioned=1 && break ;;
	esac
	sleep 2
done
[ "${conditioned:-0}" = 1 ] ||
	fail "이상치가 왜 싸 보이는지 말하지 않는다 (정렬은 collate \"C\"로 고정): $queue"

echo "--- 5-1g) 조건부 가격: 원문의 태그가 딜까지 도달한다 (BM-02 AC-2) ---"
# collector는 `카할` 같은 조건 태그를 뽑아 raw._derived.applied_conditions에 싣는다(담을 컬럼이 없어서).
# 그런데 `deal_event.applied_conditions`는 V1에 있으면서 **아무도 쓰지 않았다** — 골든 실측으로
# 뽐뿌 9.5% · 펨코 15%가 조건부인데 전부 무조건 가격의 얼굴을 하고 있었다(docs/91 Q-46).
# 분포는 as-posted가 맞다(역산 금지). **태그가 남는가**를 종단으로 본다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "조건부 원문 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status, raw)
values ('ppomppu', 'cond-e2e', 'https://example.invalid/cond', '이상치테스트 256GB 특가', 980000, now(), 'ACTIVE',
        '{"_derived":{"applied_conditions":["카할"]}}'::jsonb);
SQL

# 파이프라인이 ingest → 조건태그 순으로 돌아 같은 틱에 태그가 붙는다.
for _ in $(seq 20); do
	tagged=$(compose exec -T postgres psql -qAt -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" -c "
		select coalesce(array_to_string(de.applied_conditions, ','), '')
		  from deal_event de
		  join deal_event_source des on des.deal_event_id = de.id
		  join raw_deal_post rp on rp.id = des.raw_deal_post_id
		 where rp.post_id = 'cond-e2e'" 2>/dev/null | tr -d '\r' | head -1) || true
	[ "$tagged" = "카할" ] && break
	sleep 2
done
[ "${tagged:-}" = "카할" ] ||
	fail "조건 태그가 원문에서 딜로 도달하지 않았다 (raw._derived → deal_event.applied_conditions): '${tagged:-없음}'"

# 조건부여도 가격은 그대로 분포에 들어간다 — as-posted, base_price 역산 없음(BM-02 AC-2).
base_price=$(compose exec -T postgres psql -qAt -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" -c "
	select coalesce(de.base_price::text, 'NULL')
	  from deal_event de
	  join deal_event_source des on des.deal_event_id = de.id
	  join raw_deal_post rp on rp.id = des.raw_deal_post_id
	 where rp.post_id = 'cond-e2e'" 2>/dev/null | tr -d '\r' | head -1) || true
[ "$base_price" = "NULL" ] || fail "조건부 가격을 역산했다(base_price=$base_price). AC-2는 역산을 금지한다"

# `deal_event.shipping`은 항상 0이어야 한다 — 배송비는 이미 `headline_price`에 합산된다(BM-02:
# 저장 기준 = 실결제가 + 배송비). collector가 shipping을 별도로 채우기 시작하면 **이중계산**이다.
# 미매핑 컬럼의 "의도적 0"을 계약으로 못박는다(Q-68: confidence는 반대로 죽은 자리다).
shipping=$(compose exec -T postgres psql -qAt -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" -c "
	select coalesce(max(shipping)::text, '0') from deal_event" 2>/dev/null | tr -d '\r' | head -1) || true
[ "$shipping" = "0" ] || fail "deal_event.shipping이 0이 아니다($shipping) — 배송비 이중계산 위험(headline에 이미 합산됨)"

# 배송비 미상 표식(collector 정본 → core 사본)이 DB를 건너 실제로 검색된다.
# 표식이 어긋나면 core는 조용히 0을 세며 "오염 없음"이라고 말한다(scripts/check-tag-contract.sh).
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "배송비 미상 원문 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status, raw)
values ('ppomppu', 'ship-e2e', 'https://example.invalid/ship', '이상치테스트 256GB 특가', 970000, now(), 'ACTIVE',
        '{"_derived":{"applied_conditions":["유료배송(금액미상)","배송비미상"]}}'::jsonb);
SQL

for _ in $(seq 20); do
	unknown=$(compose exec -T postgres psql -qAt -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" -c "
		select count(*) from deal_event where '배송비미상' = any(applied_conditions)" 2>/dev/null | tr -d '\r' | head -1) || true
	[ "${unknown:-0}" -ge 1 ] && break
	sleep 2
done
[ "${unknown:-0}" -ge 1 ] ||
	fail "배송비 미상 표식이 deal_event에 도달하지 않았다 — core는 표본 오염률을 영원히 0으로 본다"

echo "--- 5-1h) 병합·교차검증(BM-04): 두 사이트의 같은 딜이 하나로 병합되고 VERIFIED가 된다 ---"
# 병합 경로는 **일어나는 쪽**이 종단으로 한 번도 검증된 적이 없었다 — 스모크는 `merged=0`(병합 안 됨)만 봤다.
# `DealMergePolicy`(±2% / 48h)는 단위 테스트만 있었다. 임계를 **넘기는** 시나리오를 하나 만든다(docs/99).
# 별도 제품으로 격리 — 다른 단계의 원문과 섞이면 분포가 흔들린다.
merge_payload=$(mktemp)
cat >"$merge_payload" <<'JSON'
{"name":"병합테스트 제품","category":"test","demandAxisMode":"GROUPED",
 "axes":[{"axisType":"PRICE","name":"용량","allowedValues":["256GB"]}],
 "variants":[{"label":"256GB","priceAxisValues":{"용량":"256GB"}}],
 "aliases":["병합테스트"]}
JSON
merge_product=$(curl -fsS -X POST "${WEB}/api/v1/products" -H 'Content-Type: application/json' \
	-d @"$merge_payload") || fail "병합용 제품 등록 실패"
rm -f "$merge_payload"
merge_pid=$(echo "$merge_product" | sed 's/.*"productId"[: ]*\([0-9]*\).*/\1/')
merge_vid=$(curl -fsS "${WEB}/api/v1/products/${merge_pid}/variants" | sed 's/[^0-9]*\([0-9]*\).*/\1/')

# 900,000 vs 910,000 → 차 10,000 ≤ max(900000×0.02=18,000, 5,000). 같은 captured_at → 48h 이내.
# 서로 다른 사이트(ppomppu·ruliweb)라 병합 시 sites 2개 → ACTIVE→VERIFIED 교차검증 전이.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "병합용 원문 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status) values
 ('ppomppu','merge-a','https://example.invalid/ma','병합테스트 256GB 특가', 900000, now(), 'ACTIVE'),
 ('ruliweb','merge-b','https://example.invalid/mb','병합테스트 256GB 특가', 910000, now(), 'ACTIVE');
SQL

# 두 원문이 하나의 딜로 병합돼 VERIFIED가 될 때까지 기다린다. 카운터가 아니라 **결과 행**을 본다
# ("무엇을 출력했는가"로 "어떻게 끝났는가"를 단언하지 않는다 — docs/99).
for _ in $(seq 20); do
	merge_state=$(compose exec -T postgres psql -qAt -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" -c "
		select count(*) || ':' || coalesce(max(status),'none') || ':' || coalesce(bool_or(cross_verified)::text,'false')
		  from deal_event where variant_id = ${merge_vid}" 2>/dev/null | tr -d '\r' | head -1) || true
	[ "$merge_state" = "1:VERIFIED:true" ] && break
	sleep 2
done
[ "$merge_state" = "1:VERIFIED:true" ] ||
	fail "두 사이트 딜이 병합되지 않았다 (기대 '1:VERIFIED:true' = 딜 1개·교차검증). 실제: '${merge_state:-없음}'"

# 병합됐으니 표본은 1건인데 **교차검증 m=1**이다 — "n건(교차 m건)"의 m이 여기서 산다(절대 원칙 1).
mbench=$(curl -fsS "${WEB}/api/v1/variants/${merge_vid}/benchmark?periodMonths=6")
echo "$mbench" | grep -q '"n":1' || fail "병합 후 표본이 1건이 아니다(딜이 안 합쳐졌다): $mbench"
echo "$mbench" | grep -q '"m":1' || fail "병합됐는데 교차검증 m이 1이 아니다: $mbench"

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

# 계약 드리프트: 구매 화면(purchase/present.ts)이 읽는 필드가 전부 있는가.
# PurchaseObservation 5필드 + 중첩 ObservationContext 6필드 — 이름 하나만 바뀌어도 화면이 undefined를 그린다.
for field in purchaseId state paidPrice purchasedAt context; do
	echo "$observations" | grep -q "\"${field}\":" ||
		fail "web PurchaseObservation이 기대하는 필드 '${field}'가 응답에 없다 (계약 드리프트): $observations"
done
for field in mode activeLowestPriceLast overpaidWon overpaidPct observationDay cheaperChanceCount; do
	echo "$observations" | grep -q "\"${field}\":" ||
		fail "web ObservationContext가 기대하는 필드 '${field}'가 응답에 없다 (계약 드리프트): $observations"
done

echo "--- 5-2b) 관찰 만료: OBSERVING → REPORT_PENDING (PUR-01) ---"
# `Purchase.expire()`·`isExpired()`는 순수 도메인에 있었지만 **부르는 사람이 없었다.**
# 관찰이 영원히 끝나지 않아 "산 뒤 알림"(PUR-03)이 3년 전 구매에도 계속 발화했을 것이다.
# 90일을 기다릴 수 없으니 purchased_at을 과거로 밀어 넣는다(REST엔 그 손잡이가 없다 — 없어야 맞다).
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<SQL || fail "만료 대상 구매 삽입 실패"
insert into purchase (variant_id, paid_price, purchased_at, observation_days, state)
values (${variant_id}, 777000, now() - interval '100 days', 90, 'OBSERVING');
SQL

for _ in $(seq 20); do
	observations=$(curl -fsS "${WEB}/api/v1/variants/${variant_id}/purchases" || true)
	case "$observations" in
	*'"state":"REPORT_PENDING"'*) expired=1 && break ;;
	esac
	sleep 2
done
[ "${expired:-0}" = 1 ] || fail "관찰 기간이 끝났는데 REPORT_PENDING으로 넘어가지 않는다: $observations"
echo "$observations" | grep -q '"mode":"REPORT_PENDING"' || fail "만료된 관찰의 문맥이 REPORT_PENDING이 아니다"
# 아직 관찰 중인 구매(5-2에서 만든 것)는 건드리지 않는다 — 만료는 기간이 끝난 것만 옮긴다.
echo "$observations" | grep -q '"state":"OBSERVING"' || fail "관찰 중인 구매까지 만료시켰다: $observations"

# OBS-02: 조용히 도는 만료는 안 도는 만료와 구별되지 않는다.
compose logs --no-log-prefix core 2>&1 | grep 'pipeline tick' | grep -q 'purchasesExpired=1' ||
	fail "틱 카운터에 purchasesExpired=1이 없다"

echo "--- 5-3) 최초부터 품절인 원문은 같은 틱에 ENDED로 닫힌다 (Q-27 ③ 자가치유) ---"
# ⚠️ `IngestDealsUseCase:137`은 원문 상태와 무관하게 딜을 ACTIVE로 만들고 :110에서 곧바로
# 알림 판정을 태운다. 파이프라인 순서(ingest → 가격 → 종료) 덕에 **DB는 같은 틱에 자가치유**되지만,
# **알림은 이미 나간 뒤다.** 텔레그램이 스텁인 지금은 로그뿐이지만 Q-20이 켜지면 실전송된다 → Q-27 ③.
# 다른 사이트(ruliweb)·다른 variant(512GB)를 써서 기존 딜과 병합되지 않게 한다.
compose exec -T postgres psql -q -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
	-v ON_ERROR_STOP=1 >/dev/null <<'SQL' || fail "품절 원문 삽입 실패"
insert into raw_deal_post (site, post_id, url, title, headline_price, captured_at, status)
values ('ruliweb', 'smoke-2', 'https://example.invalid/2', '스모크 제품 512GB 특가', 1200000, now(), 'SOLD_OUT');
SQL

deal_status() {
	compose exec -T postgres psql -qtA -U "${DB_USER:-hogumeter}" -d "${DB_NAME:-hogumeter}" \
		-c "select status from deal_event where variant_id = ${variant_id}" | tr -d '\r'
}
for _ in $(seq 20); do
	[ "$(deal_status)" = "ENDED" ] && healed=1 && break
	sleep 2
done
[ "${healed:-0}" = 1 ] || fail "최초부터 품절인 원문이 ENDED로 닫히지 않았다 (status=$(deal_status))"

echo "--- 6) collector는 opt-in 없이 네트워크를 만지지 않는다 (OBS-01 구조화 로그) ---"
# 로그는 JSON Lines다. 문장을 grep하지 말고 이벤트를 본다 — 문구는 바뀌어도 계약은 안 바뀐다.
collector_log=$(compose logs --no-log-prefix collector 2>&1 | grep '^{' | tail -1)
echo "$collector_log" | grep -q '"event":"refused"' || fail "collector가 refused 이벤트를 내지 않았다: $collector_log"
echo "$collector_log" | grep -q '"reason":"network_opt_in_missing"' || fail "정지 사유가 기록되지 않았다"

echo "--- 6-1) collector 수명 계약: exit 0 · 재시작 없음 · on-failure (프로세스 밖 계약) ---"
# 어떤 단위 테스트도 이걸 못 본다. `main()`이 0을 돌려주는 것은 `test_main.py`가 보지만,
# **compose가 그 0을 어떻게 대접하는가**는 프로세스 밖의 계약이다.
# `restart: always`로 바뀌면 opt-in 꺼진 컨테이너가 refused를 영원히 반복한다 — 그런데
# 위의 `tail -1` grep은 그때도 통과한다. 그래서 종료 코드와 재시작 횟수를 직접 본다.
collector_cid=$(compose ps -aq collector)
[ -n "$collector_cid" ] || fail "collector 컨테이너를 찾지 못했다"

# 정상 종료를 기다린다(빌드 직후엔 아직 running일 수 있다).
for _ in $(seq 30); do
	collector_state=$(docker inspect -f '{{.State.Status}}' "$collector_cid" 2>/dev/null || echo "?")
	[ "$collector_state" = "exited" ] && break
	sleep 1
done
[ "$collector_state" = "exited" ] || fail "opt-in이 꺼졌는데 collector가 종료하지 않았다(status=$collector_state)"

collector_facts=$(docker inspect \
	-f '{{.State.ExitCode}}:{{.RestartCount}}:{{.HostConfig.RestartPolicy.Name}}' "$collector_cid")
[ "$collector_facts" = "0:0:on-failure" ] ||
	fail "collector 수명 계약 위반 (exitCode:restartCount:policy = $collector_facts, 기대: 0:0:on-failure)"

# 재시작 루프면 refused가 여러 줄이다. 여기선 `grep -c`가 맞다 — 이벤트가 줄당 하나이므로.
refused_count=$(compose logs --no-log-prefix collector 2>&1 | grep -c '"event":"refused"')
[ "$refused_count" = 1 ] || fail "refused 이벤트가 ${refused_count}번 났다 — 재시작 루프를 도는 중이다"

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
# **데이터는 전부 /api 뒤에 있다.** nginx.conf는 "server 레벨이라 /api 프록시에도 적용된다"고
# 단언하지만 아무것도 확인하지 않았다 — `location /api/`에 `auth_basic off;` 한 줄이 들어가면
# 정적 페이지는 막힌 채 **API만 열린다.** 그 이미지는 이 컨테이너 안에 core가 없으므로,
# 인증을 통과하면 502(업스트림 없음), 못 하면 401이다. 둘의 차이가 곧 인증 여부다.
code_api_no_creds=$(curl -s -o /dev/null -w '%{http_code}' "${auth_url}api/v1/products")
code_api_with_creds=$(curl -s -o /dev/null -w '%{http_code}' -u smoke:smoke-pass "${auth_url}api/v1/products")
# 운영 배포 후 "정말 켜졌나"를 물을 때 볼 그 마커. 끈 분기는 0-4가 본다.
auth_marker=$(docker logs "$auth_cid" 2>&1 | grep -c 'SEC-02 basic_auth=on' || true)
docker rm -f "$auth_cid" >/dev/null
[ "$code_health" = 200 ] || fail "auth를 켜니 /healthz가 ${code_health}다 (헬스체크가 막힌다)"
[ "$code_no_creds" = 401 ] || fail "Basic Auth를 켰는데 인증 없이 ${code_no_creds}를 준다"
[ "$code_with_creds" = 200 ] || fail "올바른 자격증명인데 ${code_with_creds}를 준다"
[ "$code_api_no_creds" = 401 ] ||
	fail "**/api가 인증 없이 ${code_api_no_creds}를 준다** — 데이터가 통째로 열려 있다(SEC-02)"
[ "$code_api_with_creds" = 502 ] ||
	fail "인증을 통과한 /api가 ${code_api_with_creds}다 (기대: 502 — 이 컨테이너엔 core가 없다)"
[ "$auth_marker" -ge 1 ] || fail "auth를 켰는데 기동 로그에 'SEC-02 basic_auth=on' 마커가 없다"

echo
echo "SMOKE PASS: web -> nginx -> core -> postgres 왕복 + SEC-02 Basic Auth 확인"
