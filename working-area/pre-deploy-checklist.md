# 배포 전(go-live) 체크리스트 — 운영을 막는 것들

> **이 문서 = 운영 배포 단일 점검 지점(허브).** 로컬/테스트에선 되지만 **운영(EC2)에서만 별도 설정·적용이 필요한 것**을 한곳에.
> 개발 중 "운영에서 따로 해줘야 한다"가 드러나면 **그 자리에서** `[필수]/[권장]/[완료]`로 추가한다(말로 끝내지 않음).

## A. 데이터베이스
- **[완료]** Flyway 마이그레이션이 clean 컨테이너에서 완주 — `scripts/smoke.sh`가 매번 빈 볼륨에서 V1·V2를 돌리고 등록 API까지 왕복시킨다.
- **[완료]** 롤백 스크립트(REL-05) — V1·V2 각각에 `R1`·`R2`. `bash scripts/rollback-drill.sh`가 일회용 컨테이너에서 전진 → **역순** 후진 → 재전진을 매번 확인한다(CI `rollback` 잡). ⚠️ **롤백은 역순으로만 성립한다**(`purchase`가 `variant`·`deal_event`를 참조) — 운영 롤백 시 `R2` → `R1` 순서로 수동 적용하고 `flyway_schema_history`에서 해당 버전 행을 지운다. Flyway가 자동 실행하지 않는다.
- **[필수]** Postgres 데이터 볼륨 영속화 — `pgdata` 명명 볼륨. **운영에서 `docker compose down -v` 금지**(데이터 삭제).
- **[완료]** 백업·복원 — `bash scripts/backup.sh`(pg_dump + gzip + 7일 보관, gzip 무결성 검사), `bash scripts/restore-drill.sh`(일회용 격리 컨테이너에 복원해 테이블·행·`flyway_schema_history` 확인). **리허설 실측 통과**: 제품 1건이 덤프를 거쳐 되살아났다.
- **[필수]** **cron 등록** — `10 3 * * * cd /srv/hogumeter && bash scripts/backup.sh >> backups/backup.log 2>&1`. 스크립트만 있고 스케줄은 사람이 건다.
- **[완료 — 코드]** **오프사이트 사본**(REL-04) — `scripts/offsite-upload.sh`가 덤프를 S3에 올리고 **head-object로 크기까지 대조**한다("올렸다"와 "온전히 거기 있다"는 다른 사건). `backup.sh`가 마지막 단계로 호출하며, 실패해도 로컬 덤프·보존 정리는 이미 끝난 뒤다. `bash scripts/offsite-drill.sh`가 MinIO에 대고 **운영과 같은 코드 경로**를 리허설한다(CI `offsite` 잡, 매 커밋). aws-cli는 컨테이너로만 실행 — 호스트 설치 없음(OPS-02).
- **[필수]** 오프사이트 **실 버킷·IAM 준비** — `.env`에 `BACKUP_S3_BUCKET`(+ `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`/`AWS_DEFAULT_REGION`). **비워두면 업로드를 건너뛰고 "오프사이트 없음"을 출력한다** — 조용히 성공하지 않는다. IAM은 해당 prefix에 `PutObject`/`GetObject`/`HeadObject`만. 버킷 버저닝·수명주기(예: 90일) 권장.

## B. 시크릿 · 환경설정
- **[필수]** 운영 시크릿 = EC2의 `.env`(gitignore) — 텔레그램 봇 토큰, 네이버 쇼핑 API Client ID/Secret, Postgres 비밀번호. git/CI에 평문 금지.
- **[필수]** 유출 시크릿 회전 — git history에 평문이 올라간 적 있으면 새 값으로 교체(봇 토큰·API 키 재발급 포함).
- **[필수]** 텔레그램 봇: 운영 chat_id 확인, 관리자 알림용 별도 chat(OBS-03) 설정.
- **[필수 · 선결]** **SEC-03 화이트리스트** — `TELEGRAM_ALLOWED_CHAT_IDS`를 채우고, 봇 어댑터가 그 목록 밖의 chat_id에는 **응답하지 않는지** 확인. 인라인 콜백에도 식별 검증. 지금은 봇 어댑터 자체가 없어 미구현이다(`docs/91` Q-61) — 어댑터를 만드는 커밋에 함께 들어가야 한다. 토큰만 있고 화이트리스트가 비면 **누구든 봇에게 명령을 보낼 수 있다.**
- **[필수 · 선결]** ⚠️ **봇 토큰을 넣기 전에 `docs/91` Q-27 ③을 고칠 것.** 최초 수집 시 이미 품절인 원문에 대해 **"지금 사라" 알림이 나간다**(2026-07-10 실측: `[STUB alert] intensity=GOOD price=700000` 직후 `deal_event.status=ENDED`). 지금은 `StubAlertSender`라 로그뿐이지만 토큰을 넣는 순간 **실전송된다.** core 기존 파일(`IngestDealsUseCase`) 수정이라 상대와 조율.

## C. 보안 · 네트워크
- **[완료]** ~~CORS 설정~~ **불필요해졌다.** `web` 컨테이너의 nginx가 `/api`를 `core:8080`으로 프록시하므로 브라우저에겐 **동일 오리진**이다. 교차 출처 요청이 발생하지 않아 core에 CORS를 넣을 이유가 없다. 개발(Vite 프록시)과 운영(nginx)의 동작이 같다. **단, 크롬 확장(기능4)이 core에 직접 붙으면 그때 다시 필요**해진다.
- **[필수]** **web Basic Auth를 켤 것**(SEC-02). 구현돼 있으나 **기본 off**다 — `.env`의 `WEB_BASIC_AUTH_HTPASSWD`를 채워야 활성화된다. 해시 생성: `docker run --rm httpd:2.4-alpine htpasswd -nbm hogu '비밀번호'`(compose에 넣을 땐 `$`를 `$$`로). 끈 상태로 `0.0.0.0`에 열면 **아무나 제품을 등록·삭제한다.** `scripts/smoke.sh` 7단계가 켠/끈 경로를 모두 검증한다.
  - **켰는지 확인하는 법**("켤 것"만 적어 두면 켰는지 알 수 없다):
    1. `docker compose logs web | grep 'SEC-02 basic_auth='` → **`on`** 이어야 한다(기본은 `off`).
    2. `curl -s -o /dev/null -w '%{http_code}' http://<호스트>/` → **401**. `curl … /api/v1/products` → **401**(데이터는 전부 `/api` 뒤에 있다).
    3. `curl -s -o /dev/null -w '%{http_code}' http://<호스트>/healthz` → **200**(헬스체크는 인증 뒤에 숨지 않는다).
  - ⚠️ **인증은 nginx에만 있다.** core·postgres 포트가 `0.0.0.0`에 열려 있으면 이 인증은 아무것도 막지 못한다 — §C의 노출 범위 항목과 함께 확인할 것(`scripts/smoke.sh` 0-4가 로컬에선 그걸 단언한다).
- **[필수]** **HTTPS**(SEC-02 나머지) — Caddy 또는 nginx+certbot. Basic Auth는 평문 HTTP에서 자격증명을 그대로 노출한다.
- **[필수]** core REST API 외부 노출 범위 확인 — 1인용이므로 기본 비공개(방화벽/보안그룹). compose는 `127.0.0.1:8080`으로만 개방한다.
- **[권장]** EC2 보안그룹 최소화 — Postgres 포트는 외부 미개방(컨테이너 내부 네트워크만).

## D. 빌드 · 배포
- **[필수]** 테스트 GREEN 후 빌드. `docker compose up -d` 단일 명령 기동(OPS-01). core/collector/web 계약 변경분은 **동시 배포**.
- **[권장]** GitHub Actions CI — 테스트·빌드 자동화. CI 시크릿은 평문 커밋 금지(§B와 연결).
- **[완료]** 의존성 고정(SEC-06) — 모든 컨테이너 이미지가 **버전 태그**다(`uv:0.11.28` · `gitleaks:v8.30.1` · `shellcheck:v0.11.0` · `aws-cli:2.35.19` · `minio:RELEASE.2025-09-07T16-13-09Z` · `postgres:16` · `python:3.12-slim` · `node:22-slim` · `nginx:1.29-alpine` · `eclipse-temurin:21-*`). `:latest`는 빌드마다 다른 바이너리를 산출물에 섞는다. `.github/dependabot.yml`(주 1회, 8개 생태계)이 갱신을 PR로 올린다 — **자동 머지 없음**.
- **[권장]** **Dependabot PR을 주기적으로 읽을 것.** 태그를 고정한 채 방치하면 취약점이 그대로 굳는다. 특히 `gitleaks`는 **CI와 `.githooks/pre-commit` 두 곳**에 박혀 있어 한쪽만 올리면 훅과 CI가 다른 규칙으로 돈다. 스크립트 안 `docker run` 이미지(gitleaks·shellcheck·minio·aws-cli)는 Dependabot이 보지 못하므로 **사람이 본다**.

## E. 인프라 의존성
- **[완료]** Docker Compose 서비스 전체 기동 확인 — postgres·core·**web**·collector 4서비스. `bash scripts/smoke.sh`가 빌드→기동→web 정적 자산→SPA 폴백→`/api` 프록시→등록 POST→postgres 왕복→목록 반영까지 검증한다(CI `smoke` 잡). 한글 UTF-8 왕복 포함.
- **[필수]** 볼륨 마운트 경로/권한(재배포 유실 방지) — `pgdata` 명명 볼륨. **운영에서 `docker compose down -v`를 치지 말 것**(데이터 삭제). 스모크는 전용 프로젝트 이름으로 격리돼 있다.
- **[완료]** 헬스체크(OBS-04) — postgres(`pg_isready`) · core(`/api/v1/health`, 컴포넌트별) · web(`auth_basic`이 꺼진 `/healthz`)에 compose healthcheck를 걸었다. collector는 배치라 **의도적으로 없다**. `scripts/smoke.sh` 0단계가 `compose ps`에서 실제 `healthy` 보고를 확인하고("정의했다"와 "healthy를 낸다"는 다른 사건이다), **0-1단계가 postgres만 죽여** core가 살아서 503 + `db: DOWN`을 주고 복귀하는지까지 확인한다.
- **[권장]** 호스트 포트 충돌 — `POSTGRES_PORT`/`CORE_PORT`/`WEB_PORT`로 비켜갈 수 있다(전부 `127.0.0.1` 바인딩).
- **[필수]** 이식성 확인(OPS-02) — compose만으로 다른 호스트 이전 가능해야 함. AWS 관리형 서비스 의존은 백업용 S3만 허용.

## F. 수집기(collector) 운영
- **[필수]** 폴링 커서 영속화(REL-03) 확인 — 현재 `SiteState`는 **메모리**에만 있다. 미구현 상태로 배포하면 컨테이너 재시작마다 사이트 상태(연속 실패 횟수·중지 플래그)가 소실된다. collector는 `restart: on-failure`라 정상 종료(opt-in off·SIGTERM)엔 재시작하지 않지만, 적재 연속 실패로 `exit 1`이 나면 재시작하며 커서가 사라진다. → `docs/91` Q-59(선결: `decisions-needed` D-3).
- **[필수]** 차단 자동 중지 시 **재개 수단** 확보 — 403/429를 받으면 스케줄러가 해당 사이트를 중지한다(SEC-08). 재개 경로가 **미결**(`decisions-needed.md` D-3)이라, 이 결정 없이 커서를 영속화하면 차단당한 사이트를 다시 켤 방법이 없다. 감지는 관리 알림 chat(§B)으로 되지만 복구가 안 된다.
- **[필수]** robots.txt 실 대조(SEC-08) — **명령 하나로 만들어뒀다**: `ALLOW_REAL_ROBOTS=1 bash scripts/check-robots.sh`. 핫딜 3사의 `/robots.txt`만 각 1회 조회하고, `docs/98`에 그대로 붙일 블록을 출력한다. DISALLOW가 하나라도 있으면 **exit 1** — "확인 완료"가 아니라 "사람이 결정해야 함"이다(D-3 재개 경로 없이는 수집을 켜지 말 것). **에이전트는 이걸 돌리지 않는다**(정지조건: 실 사이트 호출). 도구 자체의 코드 경로는 `bash scripts/check-robots-drill.sh`가 로컬 서버로 리허설한다(CI `robots` 잡).
- **[필수 · 선결]** ⚠️ **폴링을 켜기 전에 `docs/91` Q-46을 고칠 것.** collector가 뽑는 조건 태그(`카할`=카드할인 · `유료배송(금액미상)` · 펨코 `조건부무료배송:와우무배`)가 **`raw` jsonb에 갇혀 `deal_event`에 도달하지 않는다** — `DealEvent` 도메인 record에 필드가 없고 `DealEventEntity`도 미매핑이다(컬럼은 V1에 있다). 골든 실측: **뽐뿌 21딜 중 2건(9.5%), 펨코 20딜 중 3건(15%)**. 즉 폴링을 켜는 순간 **표본의 약 1할이 조건부 가격인데 무조건 가격으로** 기준가에 섞이고, `유료배송(금액미상)`은 배송비 0을 더해 **실제보다 낮게** 저장된다. 화면도 알림도 조건을 한 글자도 말하지 않고 **아무 로그도 남지 않는다**(절대 원칙 1). core 기존 파일 수정이라 상대와 조율.
- **[필수]** 실 네트워크 폴링 opt-in — `collector`는 `COLLECTOR_ALLOW_NETWORK=1` 없이는 어떤 요청도 보내지 않는다(정지조건의 기계적 강제). 운영 compose에서 이 환경변수를 **의도적으로** 켜야 수집이 시작된다.
- **[완료]** 재시작·종료 계약 — 상주 3종(postgres·core·web)은 `restart: unless-stopped`, collector는 `restart: on-failure`(opt-in off의 exit 0엔 재시작 안 함). `SIGTERM`이면 현재 사이클을 마치고 종료(`stop_grace_period: 30s`). 적재 연속 3회 실패 시 `giving_up` 후 exit 1 — **수집은 되는데 저장이 안 되는 상태로 계속 돌지 않는다**.
- **[권장]** `giving_up`·`sink_error` 이벤트를 관리 알림 chat(§B)으로 흘릴 것. 지금은 `docker logs`에만 남는다.
- **[완료]** DB 적재기(Q-36 해소) — `db/raw_deal_sink.py`가 `raw_deal_post`에 `(site, post_id)` 업서트한다. `DB_HOST`가 없으면 적재하지 않고 그 사실을 `started` 이벤트에 남긴다. **운영 compose는 `DB_HOST`를 주입한다**(설정 누락 시 수집은 도는데 표본이 0으로 남으므로 기동 로그에서 `"sink":"postgres"`를 확인할 것).
- **[완료]** **파이프라인 트리거**(Q-27 ⑤) — core의 `PipelineScheduler`가 `CORE_PIPELINE_INTERVAL_MS`(기본 60000)마다 `raw_deal_post`를 소비한다. **2026-07-10까지 이게 없어 수집된 원문을 아무도 읽지 않았다.** 기동 후 `docker logs`에서 `"message":"pipeline tick …"`이 주기적으로 찍히는지 확인할 것. `pending`이 단조 증가하면 매칭이 전부 실패하고 있다는 뜻이다(`review_queue_item` 확인).
- **[권장]** 로그 형식 — `CORE_LOG_FORMAT=ecs`(기본)면 core도 collector처럼 JSON을 낸다(OBS-01). 로그 수집기를 붙일 때 두 컨테이너를 한 파서로 읽을 수 있다. 빈 값이면 텍스트로 되돌아간다.

<!-- 각 항목은 프로젝트에 맞게 추가/삭제. 완료분은 [완료]로 표기하고 decision-log에 남긴다. -->
