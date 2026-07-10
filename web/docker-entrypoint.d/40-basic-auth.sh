#!/bin/sh
# SEC-02 web 접근 통제 — Basic Auth. nginx 공식 엔트리포인트가 기동 전에 실행한다.
#
# `WEB_BASIC_AUTH_HTPASSWD`가 있으면 켜고, 없으면 끈다(로컬 개발 편의).
# **평문 비밀번호를 받지 않는다** — htpasswd 해시 한 줄을 그대로 받는다:
#
#   docker run --rm httpd:2.4-alpine htpasswd -nbm hogu '비밀번호'
#
# 끈 상태로 공개망(0.0.0.0)에 노출하면 아무나 제품을 등록·삭제한다. compose는
# 127.0.0.1에만 바인딩한다 — pre-deploy-checklist §C.

set -eu

conf=/etc/nginx/conf.d/auth.inc

if [ -n "${WEB_BASIC_AUTH_HTPASSWD:-}" ]; then
	printf '%s\n' "$WEB_BASIC_AUTH_HTPASSWD" >/etc/nginx/.htpasswd
	# 워커는 nginx 사용자로 돈다. 600 root:root면 `open() failed (13: Permission denied)`로
	# 인증 시도마다 500이 난다(401은 정상적으로 나므로 오히려 헷갈린다).
	# 세상에 열지 않되 워커 그룹에만 읽기를 준다.
	chown root:nginx /etc/nginx/.htpasswd
	chmod 640 /etc/nginx/.htpasswd
	cat >"$conf" <<-'EOF'
		auth_basic "hogumeter";
		auth_basic_user_file /etc/nginx/.htpasswd;
	EOF
	# 관측 출력은 산문이 아니라 **기계가 읽는 마커**다(docs/99). 문구를 grep하는 테스트는 문구를
	# 굳히고, 한글은 콘솔 인코딩에 따라 깨진다. 운영 배포 후 이 한 줄로 "켜졌는가"를 확인한다.
	echo "SEC-02 basic_auth=on"
else
	echo 'auth_basic off;' >"$conf"
	echo "SEC-02 basic_auth=off (WEB_BASIC_AUTH_HTPASSWD unset - do not expose publicly)"
fi
