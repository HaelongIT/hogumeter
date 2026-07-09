# 배포 전(go-live) 체크리스트 — 운영을 막는 것들

> **이 문서 = 운영 배포 단일 점검 지점(허브).** 로컬/테스트에선 되지만 **운영(EC2)에서만 별도 설정·적용이 필요한 것**을 한곳에.
> 개발 중 "운영에서 따로 해줘야 한다"가 드러나면 **그 자리에서** `[필수]/[권장]/[완료]`로 추가한다(말로 끝내지 않음).

## A. 데이터베이스
- **[완료]** Flyway 마이그레이션이 clean 컨테이너에서 완주 — `scripts/smoke.sh`가 매번 빈 볼륨에서 V1·V2를 돌리고 등록 API까지 왕복시킨다.
- **[필수]** **V2 롤백 스크립트 부재**(REL-05) — `db/rollback/`엔 `R1__init_rollback.sql`만 있고 `V2__purchase.sql`의 롤백이 없다. 롤백을 검증하는 테스트도 없다. `docs/91` Q-51.
- **[필수]** Postgres 데이터 볼륨 영속화 — `pgdata` 명명 볼륨. **운영에서 `docker compose down -v` 금지**(데이터 삭제).
- **[완료]** 백업·복원 — `bash scripts/backup.sh`(pg_dump + gzip + 7일 보관, gzip 무결성 검사), `bash scripts/restore-drill.sh`(일회용 격리 컨테이너에 복원해 테이블·행·`flyway_schema_history` 확인). **리허설 실측 통과**: 제품 1건이 덤프를 거쳐 되살아났다.
- **[필수]** **cron 등록** — `10 3 * * * cd /srv/hogumeter && bash scripts/backup.sh >> backups/backup.log 2>&1`. 스크립트만 있고 스케줄은 사람이 건다.
- **[필수]** **주 1회 S3 offsite 사본**(REL-04) — 미구현. 로컬 디스크가 죽으면 백업도 함께 죽는다.

## B. 시크릿 · 환경설정
- **[필수]** 운영 시크릿 = EC2의 `.env`(gitignore) — 텔레그램 봇 토큰, 네이버 쇼핑 API Client ID/Secret, Postgres 비밀번호. git/CI에 평문 금지.
- **[필수]** 유출 시크릿 회전 — git history에 평문이 올라간 적 있으면 새 값으로 교체(봇 토큰·API 키 재발급 포함).
- **[필수]** 텔레그램 봇: 운영 chat_id 확인, 관리자 알림용 별도 chat(OBS-03) 설정.

## C. 보안 · 네트워크
- **[완료]** ~~CORS 설정~~ **불필요해졌다.** `web` 컨테이너의 nginx가 `/api`를 `core:8080`으로 프록시하므로 브라우저에겐 **동일 오리진**이다. 교차 출처 요청이 발생하지 않아 core에 CORS를 넣을 이유가 없다. 개발(Vite 프록시)과 운영(nginx)의 동작이 같다. **단, 크롬 확장(기능4)이 core에 직접 붙으면 그때 다시 필요**해진다.
- **[필수]** **web Basic Auth를 켤 것**(SEC-02). 구현돼 있으나 **기본 off**다 — `.env`의 `WEB_BASIC_AUTH_HTPASSWD`를 채워야 활성화된다. 해시 생성: `docker run --rm httpd:2.4-alpine htpasswd -nbm hogu '비밀번호'`(compose에 넣을 땐 `$`를 `$$`로). 끈 상태로 `0.0.0.0`에 열면 **아무나 제품을 등록·삭제한다.** `scripts/smoke.sh` 7단계가 켠/끈 경로를 모두 검증한다.
- **[필수]** **HTTPS**(SEC-02 나머지) — Caddy 또는 nginx+certbot. Basic Auth는 평문 HTTP에서 자격증명을 그대로 노출한다.
- **[필수]** core REST API 외부 노출 범위 확인 — 1인용이므로 기본 비공개(방화벽/보안그룹). compose는 `127.0.0.1:8080`으로만 개방한다.
- **[권장]** EC2 보안그룹 최소화 — Postgres 포트는 외부 미개방(컨테이너 내부 네트워크만).

## D. 빌드 · 배포
- **[필수]** 테스트 GREEN 후 빌드. `docker compose up -d` 단일 명령 기동(OPS-01). core/collector/web 계약 변경분은 **동시 배포**.
- **[권장]** GitHub Actions CI — 테스트·빌드 자동화. CI 시크릿은 평문 커밋 금지(§B와 연결).

## E. 인프라 의존성
- **[완료]** Docker Compose 서비스 전체 기동 확인 — postgres·core·**web**·collector 4서비스. `bash scripts/smoke.sh`가 빌드→기동→web 정적 자산→SPA 폴백→`/api` 프록시→등록 POST→postgres 왕복→목록 반영까지 검증한다(CI `smoke` 잡). 한글 UTF-8 왕복 포함.
- **[필수]** 볼륨 마운트 경로/권한(재배포 유실 방지) — `pgdata` 명명 볼륨. **운영에서 `docker compose down -v`를 치지 말 것**(데이터 삭제). 스모크는 전용 프로젝트 이름으로 격리돼 있다.
- **[완료 — 임시방편]** 헬스체크(OBS-04) — postgres·core·web에 compose healthcheck를 걸었다. web은 `auth_basic`이 꺼진 `/healthz`, core는 `/api/v1/products`(actuator 부재, `docs/91` Q-50). collector는 배치라 **의도적으로 없다**. `scripts/smoke.sh` 0단계가 `compose ps`에서 실제 `healthy` 보고를 확인한다 — "정의했다"와 "healthy를 낸다"는 다른 사건이다.
- **[권장]** 호스트 포트 충돌 — `POSTGRES_PORT`/`CORE_PORT`/`WEB_PORT`로 비켜갈 수 있다(전부 `127.0.0.1` 바인딩).
- **[필수]** 이식성 확인(OPS-02) — compose만으로 다른 호스트 이전 가능해야 함. AWS 관리형 서비스 의존은 백업용 S3만 허용.

## F. 수집기(collector) 운영
- **[필수]** 폴링 커서 영속화(REL-03) 확인 — 현재 `SiteState`는 **메모리**에만 있다. 미구현 상태로 배포하면 컨테이너 재시작마다 사이트 상태(연속 실패 횟수·중지 플래그)가 소실되고, `restart: always`라 이게 조용히 반복된다. → `docs/91` Q-36.
- **[필수]** 차단 자동 중지 시 **재개 수단** 확보 — 403/429를 받으면 스케줄러가 해당 사이트를 중지한다(SEC-08). 재개 경로가 **미결**(`decisions-needed.md` D-3)이라, 이 결정 없이 커서를 영속화하면 차단당한 사이트를 다시 켤 방법이 없다. 감지는 관리 알림 chat(§B)으로 되지만 복구가 안 된다.
- **[완료 — 단 실 대조 필요]** robots.txt 준수 게이트(SEC-08) — `scheduler/fetcher.py`의 `RobotsGate`로 구현(Q-38 해소). **⚠️ fake opener 테스트만 통과했다.** 실 수집 가동 전에 뽐뿌·루리웹·펨코의 **실제 robots.txt를 1회 조회**해 (a) 우리 리스트 URL이 Disallow인지 (b) Crawl-delay 선언이 있는지 확인하고 `docs/98`에 기록할 것. Disallow면 그 사이트는 자동 중지되며 D-3(재개 경로) 없이는 되살릴 수 없다.
- **[필수]** 실 네트워크 폴링 opt-in — `collector`는 `COLLECTOR_ALLOW_NETWORK=1` 없이는 어떤 요청도 보내지 않는다(정지조건의 기계적 강제). 운영 compose에서 이 환경변수를 **의도적으로** 켜야 수집이 시작된다.
- **[완료]** 재시작·종료 계약 — 상주 3종(postgres·core·web)은 `restart: unless-stopped`, collector는 `restart: on-failure`(opt-in off의 exit 0엔 재시작 안 함). `SIGTERM`이면 현재 사이클을 마치고 종료(`stop_grace_period: 30s`). 적재 연속 3회 실패 시 `giving_up` 후 exit 1 — **수집은 되는데 저장이 안 되는 상태로 계속 돌지 않는다**.
- **[권장]** `giving_up`·`sink_error` 이벤트를 관리 알림 chat(§B)으로 흘릴 것. 지금은 `docker logs`에만 남는다.
- **[필수]** collector는 아직 **DB에 아무것도 쓰지 않는다**(`docs/91` Q-36). opt-in해도 화면 출력뿐이다. 적재기 없이 배포하면 수집은 도는데 기준가는 영원히 표본 0이다.

<!-- 각 항목은 프로젝트에 맞게 추가/삭제. 완료분은 [완료]로 표기하고 decision-log에 남긴다. -->
