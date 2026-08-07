# B3 — 보안 표면 횡단 리뷰

## 요약 (High 0 / Medium 1 / Low 2 / Info 2)
1인용 EC2 배포 전제에서 시크릿 취급·헬스 노출·확장 ingest 인증은 이미 이 프로젝트의 원칙(닫힌 기본값·예외 타입만 노출·상수시간 비교)을 정확히 지키고 있다. 실제 남은 구멍은 코드가 아니라 **"공개 노출을 켜는 순간과 그것을 검증하는 순간이 프로그램적으로 묶여 있지 않다"**는 절차 갭(B3-01, Medium) 하나이고, 나머지는 1인용 맥락에서 낮은 우선순위의 하드닝 부재·문서 드리프트다.

### B3-01 — `--profile public` 공개 노출이 `preflight.sh prod` 통과에 강제로 묶여 있지 않다 · Medium
- **위치**:
  - `docker-compose.yml:99-120` (`caddy` 서비스, `profiles: ["public"]`, `ports: ["80:80","443:443"]` — 유일하게 루프백 제한이 없는 진입점)
  - `web/docker-entrypoint.d/40-basic-auth.sh:30-33` (`WEB_BASIC_AUTH_HTPASSWD` 비면 `auth_basic off;`를 쓰고 **경고 로그만 남기고 정상 기동**)
  - `scripts/preflight.sh:70-77` (prod 모드에서 `WEB_BASIC_AUTH_HTPASSWD` 비면 FAIL — 하지만 이 스크립트는 `docker compose`를 부르지 않는 **별개의 사람 실행 스크립트**)
  - `scripts/smoke.sh` 전체 — `grep -n "profile public|caddy"` 매치 0건. `--profile public` 경로(=caddy가 실제로 뜬 상태에서 web 인증을 검사하는 경로)는 CI 스모크가 한 번도 실행하지 않는다.
- **근거**: `40-basic-auth.sh`는 `WEB_BASIC_AUTH_HTPASSWD`가 비어도 "`echo "SEC-02 basic_auth=off (WEB_BASIC_AUTH_HTPASSWD unset - do not expose publicly)"`"만 찍고 web 컨테이너는 정상 기동한다. `caddy`는 web의 인증 상태를 전혀 모른 채 그냥 `reverse_proxy web:80`(`Caddyfile:12`)한다. `preflight.sh`는 `mode=prod`일 때만 이 값을 강제하는데(`scripts/preflight.sh:70-77`), 이 검사를 통과시키는 것과 실제로 `docker compose --profile public up -d`를 실행하는 것 사이에는 **아무 기계적 연결이 없다** — 별도 명령 두 개를 사람이 순서대로 기억해서 쳐야 한다.
- **실패 시나리오**: 운영자가 EC2에서 `.env`에 `DOMAIN`만 채우고 `WEB_BASIC_AUTH_HTPASSWD`를 빠뜨린 채(혹은 `preflight.sh prod`를 건너뛰고) `docker compose --profile public up -d`를 실행하면, 인터넷 전체에서 `https://<DOMAIN>/`로 제품 등록·구매기록·기준가 데이터를 **인증 없이** 읽고 쓸 수 있는 상태가 그대로 기동된다. 발견은 오직 `docker compose logs web | grep 'SEC-02 basic_auth='`을 사람이 직접 돌려봐야만 가능하다(`pre-deploy-checklist.md:35` 그 방법을 문서화는 해 두었다).
- **이미 막는 장치 확인**: `scripts/preflight.sh prod`가 이 조합을 **검사할 수는** 있다(코드로 확인: FAIL 분기 존재). `scripts/smoke.sh` 0-4가 **기본** `docker compose up -d`에서 core·postgres·web 포트가 127.0.0.1인지는 검증하지만(`scripts/smoke.sh:182-206`), `--profile public` 프로파일로 caddy가 뜬 상태에서 web 인증이 실제로 강제되는지는 CI 어디에서도 실행되지 않는다(grep 0건). `pre-deploy-checklist.md:32-38`이 사람이 손으로 확인하는 절차(curl 401 확인 등)를 잘 문서화해 뒀지만, 이 역시 **강제**가 아니라 **안내**다.
- **권고**: 최소한 ⓐ CI에 `--profile public` 경로를 (MinIO/offsite 리허설처럼) 격리 스택으로 한 번 더 리허설하는 잡을 추가해 "WEB_BASIC_AUTH_HTPASSWD 없이 --profile public을 올리면 최소한 경고/차단이 눈에 띈다"는 계약을 만들거나, ⓑ `docker-compose.yml`의 `caddy` 서비스에 `depends_on`으로 web의 헬스가 아니라 "auth on" 마커를 확인하는 게이트 컨테이너를 두거나, ⓒ 최소한 배포 문서에 "`--profile public up -d` 앞에 반드시 `preflight.sh prod`를 실행하는 한 줄짜리 래퍼 스크립트"를 만들어 두 단계를 기계적으로 묶는다. 어느 쪽이든 지금은 "정말 잡는지"가 사람의 기억에 있다.

### B3-02 — core·collector 컨테이너가 root로 실행된다 · Low
- **위치**: `core/Dockerfile:9-12` (`FROM eclipse-temurin:21-jre` 이후 `USER` 지시 없음), `collector/Dockerfile:1-10`(`FROM python:3.12-slim` 이후 `USER` 지시 없음)
- **근거**: 두 Dockerfile 모두 `USER` 지시가 없어 기본 이미지의 root로 프로세스가 돈다. (참고: `web/Dockerfile`은 최종 스테이지가 `nginx:1.29-alpine`이고 이 베이스 이미지는 기본 `nginx.conf`에 `user nginx;`가 있어 워커 프로세스는 이미 비루트다 — 이 항목에서 제외.)
- **실패 시나리오**: core 또는 collector 프로세스에서 RCE급 취약점(역직렬화·의존성 CVE 등)이 터지면 공격자는 컨테이너 안에서 root 권한을 그대로 얻는다. `docker-compose.yml`에 `docker.sock` 마운트·`privileged`·`cap_add`·`network_mode: host`가 전혀 없어(확인: 3개 grep 전부 0건) 컨테이너 탈출 경로 자체는 열려 있지 않으므로, 이 root 권한의 실질 피해 반경은 "그 컨테이너 안"으로 제한된다.
- **이미 막는 장치 확인**: 없음. Dependabot(`.github/dependabot.yml`)이 베이스 이미지·의존성 버전은 추적하지만 런타임 사용자 권한은 다루지 않는다.
- **권고**: `core/Dockerfile`·`collector/Dockerfile`에 비루트 `USER` 추가(예: `RUN useradd -r appuser` + `USER appuser`). 1인용 EC2·컨테이너 탈출 경로 부재라는 맥락에서 실질 위험은 낮아 Low로 판정 — High로 올릴 근거(권한 상승으로 호스트나 다른 컨테이너에 닿는 구체 경로)는 찾지 못했다.

### B3-03 — SEC-03(텔레그램 인바운드 화이트리스트) 관련 문서 2곳이 실제 구현과 어긋난다 · Info
- **위치**: `.env.example:39-41` ("아직 인바운드 핸들러가 없어 소비되지 않는다"), `working-area/pre-deploy-checklist.md:27` ("지금은 인바운드 핸들러 자체가 없어 미소비")
- **근거**: 실제로는 `core/src/main/java/dev/hogumeter/core/adapter/telegram/TelegramInboundPoller.java`(전체)와 `core/src/main/java/dev/hogumeter/core/application/ReviewCallbackRouter.java:34-38`이 이미 구현돼 있고, `TELEGRAM_ALLOWED_CHAT_IDS`가 비면 `TELEGRAM_CHAT_ID`로 폴백하며 **둘 다 비면 빈 허용집합(아무도 허용 안 함)**으로 닫힌다(`ReviewCallbackRouter.java:113-122`, `parseChats`). 코드는 안전한 방향(닫힌 기본값)으로 이미 앞서 있고, 문서만 뒤처졌다.
- **실패 시나리오**: 위험한 방향의 드리프트는 아니다(코드가 문서보다 안전) — 다만 다음 세션의 누군가 이 문서를 믿고 "SEC-03은 아직 구현 안 됐으니 신경 안 써도 된다"고 판단해 `TELEGRAM_ALLOWED_CHAT_IDS`/`TELEGRAM_CHAT_ID` 설정을 소홀히 검토할 여지, 혹은 반대로 이미 끝난 일을 다시 구현하려 시간을 쓸 여지가 있다.
- **이미 막는 장치 확인**: 없음 — `check-board-references.sh` 등 기존 게이트는 Q-ID 인용 존재 여부만 보고 "구현됐는데 문서가 아니라고 우긴다"는 종류의 드리프트는 잡지 못한다(명시된 한계).
- **권고**: 두 파일의 해당 문장을 "구현됨, 닫힌 기본값"으로 갱신. 코드가 이미 옳으므로 리스크는 낮아 Info.

### B3-04 — 모든 시크릿이 컨테이너 환경변수로만 전달된다(파일/Docker secret 미사용) · Low
- **위치**: `docker-compose.yml:11-13,33-50,85,124-130` — `DB_PASSWORD`·`TELEGRAM_BOT_TOKEN`·`NAVER_CLIENT_ID/SECRET`·`EXTENSION_INGEST_TOKEN`·`WEB_BASIC_AUTH_HTPASSWD` 전부 `environment:` 블록의 `${VAR}` 참조.
- **근거**: Docker의 `environment:` 방식은 `docker inspect <container>`나 컨테이너 안에서 `/proc/<pid>/environ`을 읽으면 평문(또는 htpasswd처럼 해시 형태)이 그대로 보인다. Docker `secrets:`(파일 마운트) 방식과 달리 접근 통제가 "그 값을 읽을 수 있는 파일 권한"이 아니라 "docker 데몬에 닿을 수 있는가"로 뭉뚱그려진다.
- **실패 시나리오**: 이 EC2 호스트에 SSH 접근 권한을 가진(또는 탈취한) 사람이라면 `docker inspect core`·`docker inspect web` 한 번으로 DB 비밀번호·텔레그램 봇 토큰·확장 ingest 토큰을 전부 평문으로 확인할 수 있다.
- **이미 막는 장치 확인**: 없음. 다만 이 호스트에 SSH로 닿을 수 있는 사람은 이미 `.env` 파일 자체도 읽을 수 있으므로(같은 호스트, 같은 신뢰 경계), 이 항목이 추가로 여는 공격면은 사실상 없다 — 1인용 EC2 전제에서 "호스트 접근 = 이미 게임 오버"이기 때문에 Low로 판정.
- **권고**: 지금 우선순위로 올릴 근거는 약하다. 여러 사람이 같은 호스트를 공유하게 되면(범위 밖 시나리오) 그때 Docker secrets나 EC2 Secrets Manager 인젝션으로 넘기는 것을 재검토.

## 노출 표면 정리

**기본 `docker compose up -d`**

| 경로/포트 | 바인딩 | 인증 | 무엇이 닿는가 |
|---|---|---|---|
| `5432` (postgres) | `127.0.0.1` (`docker-compose.yml:24`) | 없음 | 로컬호스트 프로세스만 DB 직결 가능 |
| `8080` (core) | `127.0.0.1` (`docker-compose.yml:75`) | 앱 레벨 없음(GET 일관 설계, B1 판정). `POST /api/v1/coupang/observations`만 `X-Extension-Token` 상수시간 검증 | 로컬호스트에서만 REST 전체 접근 가능 |
| `80`→`3000` (web/nginx) | `127.0.0.1` (`docker-compose.yml:97`) | `WEB_BASIC_AUTH_HTPASSWD` 설정 시 Basic Auth(`server` 레벨이라 `/`·`/api/` 모두 포함), `/healthz`만 예외 | 로컬호스트에서만 SPA + `/api` 프록시 접근 가능 |
| `caddy` 80/443 | 뜨지 않음(`profiles: ["public"]`) | 해당없음 | 해당없음 |

**`docker compose --profile public up -d` 추가 시**

| 경로/포트 | 바인딩 | 인증 | 무엇이 닿는가 |
|---|---|---|---|
| `80`/`443` (caddy) | `0.0.0.0`(루프백 제한 없음, `docker-compose.yml:117-118`) | TLS만 종단, 인증은 뒤의 web에 위임(`Caddyfile:12` `reverse_proxy web:80`) | **`WEB_BASIC_AUTH_HTPASSWD`가 비어 있으면 인터넷 전체에 무인증으로 SPA+REST 전체(등록·삭제·구매기록) 노출**(B3-01) |
| `5432`/`8080`/`3000` | 여전히 `127.0.0.1` | 변화 없음 | caddy는 컨테이너 내부 네트워크로 web에 닿을 뿐, 이 포트들을 직접 열지 않음 |

## 검토했으나 문제없음 (근거)
- **헬스 응답 예외 유출 없음**: `HealthReport.Component.down()`이 `cause.getClass().getSimpleName()`만 담는다(`HealthReport.java:32-34`) — JDBC 접속 URL·사용자명이 새지 않는다.
- **확장 ingest 인증**: `EXTENSION_INGEST_TOKEN` 미설정 시 `configuredToken.isBlank()`로 무조건 거절(`CoupangObservationController.java:76-80`) — "설정을 지웠는데 게이트가 초록"이 아니다. 비교는 `MessageDigest.isEqual`로 상수시간(`:74,77-78`). 인증 통과 후에만 레이트리밋을 소비해(`:56-58`) 미인증 요청으로 정당한 요청의 쿼터를 고갈시키지 못한다.
- **텔레그램 봇 토큰 비유출**: 토큰은 URL 조합에만 쓰이고(`HttpTelegramApi.java:34-35`) 어떤 로그·예외 메시지에도 실리지 않는다 — `TelegramTransportException`은 원인 예외의 **클래스 이름만** 담는다(`HttpTelegramApi.java:156-160`). `TelegramInboundPoller`·`TelegramAdminNotifier`의 `log.warn` 호출도 실패 예외 객체를 넘길 뿐 URL 문자열을 직접 조립해 찍지 않는다.
- **SEC-03 인바운드 화이트리스트**: 코드 자체는 닫힌 기본값 — `TELEGRAM_ALLOWED_CHAT_IDS`·`TELEGRAM_CHAT_ID` 둘 다 비면 아무 chat_id도 허용되지 않는다(`ReviewCallbackRouter.java:113-122`, `route()`의 `allowedChats.contains` 가드 `:56-59`). 문서만 뒤처짐(B3-03).
- **actuator 미노출**: `core/build.gradle.kts`에 `spring-boot-starter-actuator` 의존성 자체가 없다(grep 0건) — `/actuator/**` 표면이 애초에 존재하지 않는다.
- **에러 응답 기본값**: `application.yml`에 `server.error.*` 오버라이드가 전혀 없어(grep 0건) Spring Boot 4의 안전한 기본값(`include-message`/`include-stacktrace` 모두 비노출)이 그대로 유지된다. `ApiExceptionHandler`가 도메인 예외를 전부 `{code, message}`로만 매핑하고(`ApiExceptionHandler.java`), 처리되지 않은 예외는 이 기본값으로 떨어진다.
- **nginx Basic Auth 범위**: `include /etc/nginx/conf.d/auth.inc;`가 `server` 블록 레벨에 있어(`nginx.conf:20`) `/`와 `/api/` 모두 인증 뒤에 있다 — `.claude/rules/web-react.md`가 경고하는 과거 함정("`/api/`에만 `auth_basic off`")은 현재 코드에 없다. `/healthz`만 명시적으로 예외(`nginx.conf:27-32`).
- **htpasswd 파일 권한**: `chown root:nginx` + `chmod 640`(`40-basic-auth.sh:21-22`)로 세상에 열려 있지 않다.
- **시크릿 스캔**: CI `secrets` 잡이 히스토리 전체(`fetch-depth: 0`)를 gitleaks로 매 push/PR 스캔하고(`ci.yml:11-20`), `.gitleaks.toml`의 예외 4건은 전부 `condition = "AND"`로 규칙+경로+리터럴 문자열을 모두 요구해 좁게 스코프돼 있다(디렉토리 전체 예외 없음).
- **`.gitignore` 계약**: `backups/`·`.env*`가 실행 가능한 계약 테스트(`scripts/check-gitignore.test.sh`, CI `secrets` 잡)로 지켜진다.
- **이미지 레이어 시크릿 없음**: `core/Dockerfile`·`web/Dockerfile`·`collector/Dockerfile` 어디에도 `ARG`로 시크릿을 받는 지점이 없다 — 빌드 시점에 시크릿이 필요 없는 구조라 레이어에 남을 경로 자체가 없다.
- **베이스 이미지 태그 고정**: 세 Dockerfile 모두 `:latest`가 아닌 구체 태그(`eclipse-temurin:21-jre`, `nginx:1.29-alpine`, `python:3.12-slim`+`uv:0.11.28`)를 쓰고 Dependabot이 `docker`/`docker-compose` 에코시스템으로 추적한다(`.github/dependabot.yml:17-37`).

## 시간·범위 한계로 못 본 것
- `--profile public` 조합을 실제로 기동해 caddy↔web 왕복(TLS 핸드셰이크·자체서명 인증서 경로)을 실행 검증하지는 않았다(정적 리뷰만, "코드/설정을 고치지 마라·컨테이너를 띄우지 마라" 지시 준수) — B3-01의 근거는 코드·CI 정의 파일 대조에 한정된다.
- EC2 보안그룹·OS 방화벽 등 코드 밖 인프라 설정은 저장소에 없어 확인 불가 — `pre-deploy-checklist.md` §C에 위임된 항목으로 남겨 둔다.
- 네이버 쇼핑 API 어댑터는 아직 core에 구현이 없어(`grep NAVER_CLIENT` core/src 0건) 검토 대상에서 제외했다.
- `.gitleaks.toml` 예외 4건이 실제로 대응하는 과거 커밋(`fc5357b`·`705d503`·`e7a2170` 등)의 diff까지는 대조하지 않았다 — allowlist 서술과 규칙·경로·정규식 필드만 정적으로 확인했다.
- collector(Python) 내부에서 `DB_PASSWORD` 등이 로그로 새는지는 collector 소스를 깊이 훑지 않아 미확인(B3 범위에 명시된 core 위주 파일 목록을 우선함).
