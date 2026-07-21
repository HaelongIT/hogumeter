#!/usr/bin/env bash
# 1차 검증/배포 전 설정 점검(SEC·REL). 스택을 띄우기 **전에**, 조용히 오작동할 설정을 잡는다 —
# 공개 배포인데 web 인증 없음(SEC-02: 데이터 노출), 텔레그램 켰는데 토큰 없음(기동 실패), 실 폴링인데
# robots 미대조(§F). "…할 것"에는 "…했는지 확인하는 법"이 붙어야 한다 — 이 스크립트가 그 확인이다.
#
# 사용: bash scripts/preflight.sh [dev|prod] [.env경로]
#   dev  = 로컬(127.0.0.1) 전용 — web 인증 선택
#   prod = 공개망 노출 — web 인증 필수
set -euo pipefail

mode="${1:-dev}"
envfile="${2:-.env}"
fail=0

# .env에서 KEY의 값을 읽는다(없으면 빈 문자열). grep 미매치는 정상이라 || true.
get() { grep -E "^$1=" "$envfile" 2>/dev/null | head -1 | cut -d= -f2- || true; }
say() { printf '  %-4s %s\n' "$1" "$2"; }

if [ ! -f "$envfile" ]; then
	echo "FAIL: $envfile 이 없습니다 — .env.example을 복사해 값을 채우세요" >&2
	exit 1
fi
case "$mode" in dev | prod) ;; *)
	echo "FAIL: mode는 dev|prod (받은 값: $mode)" >&2
	exit 1
	;;
esac

echo "── preflight ($mode · $envfile) ──"

# DB — 항상 필요(compose가 :?로 강제하지만 먼저 잡아 실패-기동 왕복을 아낀다).
if [ -z "$(get DB_PASSWORD)" ]; then
	say FAIL "DB_PASSWORD 미설정 — 스택이 뜨지 않습니다"
	fail=1
else
	say OK "DB_PASSWORD"
fi

# 텔레그램 — enabled면 토큰·chat 필수(아니면 core가 기동에서 실패하거나 알림이 갈 곳이 없다).
if [ "$(get TELEGRAM_ENABLED)" = "true" ]; then
	if [ -z "$(get TELEGRAM_BOT_TOKEN)" ]; then
		say FAIL "TELEGRAM_ENABLED=true인데 BOT_TOKEN 없음 — core 기동 실패"
		fail=1
	else
		say OK "TELEGRAM_BOT_TOKEN"
	fi
	if [ -z "$(get TELEGRAM_CHAT_ID)" ]; then
		say FAIL "TELEGRAM_ENABLED=true인데 CHAT_ID 없음 — 알림이 갈 곳이 없습니다"
		fail=1
	else
		say OK "TELEGRAM_CHAT_ID"
	fi
else
	say INFO "텔레그램 미설정 — 알림은 로그 스텁(실발송 없음). 실 발송/버튼은 TELEGRAM_ENABLED=true 뒤."
fi

# 공개 배포 — web 인증 필수(SEC-02). 비면 등록·판단·구매 데이터가 공개망에 그대로 노출된다.
if [ "$mode" = "prod" ]; then
	if [ -z "$(get WEB_BASIC_AUTH_HTPASSWD)" ]; then
		say FAIL "prod인데 WEB_BASIC_AUTH_HTPASSWD 없음 — 데이터가 공개망에 노출됩니다(SEC-02)"
		fail=1
	else
		say OK "WEB_BASIC_AUTH_HTPASSWD"
	fi
fi

# 실 폴링 — robots 실 대조 리허설은 자동 확인 불가라 경고로 남긴다(사람이 §F를 돌렸는지).
if [ "$(get COLLECTOR_ALLOW_NETWORK)" = "1" ]; then
	say WARN "COLLECTOR_ALLOW_NETWORK=1 — 배포 전 'ALLOW_REAL_ROBOTS=1 bash scripts/check-robots.sh'로 3사 robots 실 대조를 돌렸는지 확인(pre-deploy §F). 차단당한 사이트 재개 경로는 아직 없습니다(D-3)."
fi

if [ "$fail" -eq 0 ]; then
	echo "PREFLIGHT OK ($mode) — 위 FAIL 없음. 이제 docker compose up + scripts/smoke.sh."
else
	echo "PREFLIGHT FAILED ($mode) — 위 FAIL을 채운 뒤 다시 실행하세요." >&2
	exit 1
fi
