# PLACEHOLDERS — 치환 체크리스트

이 템플릿의 `{{...}}` 토큰을 새 프로젝트 값으로 **찾아바꾸기 1패스** 하면 뼈대가 완성된다.
치환 후 `<!-- 예시(교체) -->` 블록은 **본인 스택에 맞으면 두고, 아니면 지운다.**

## 필수 토큰

| 토큰 | 의미 | 예시(현 CMS) |
|---|---|---|
| `{{PROJECT_NAME}}` | 프로젝트명 | CMS |
| `{{PROJECT_ONE_LINER}}` | 한 줄 정의 | 관리자 백오피스 + 사용자 포털 CMS |
| `{{BACKEND_STACK}}` | 백엔드 스택 | Spring Boot + MyBatis |
| `{{FRONTEND_STACK}}` | 프론트 스택 | React + Vite + Zustand + Ant Design |
| `{{DB}}` | 데이터베이스 | PostgreSQL(단일 인스턴스) |
| `{{INFRA}}` | 외부 의존 | Redis(토큰), SMTP(메일) |
| `{{DEPLOY}}` | 배포 형태 | 단일 인스턴스, Nginx same-origin |
| `{{CURRENT_MODULE}}` | 현재 개발 모듈 | account / board / banner |
| `{{REPO_LAYOUT}}` | 저장소 구성 | 3 repo: 루트 docs / backend / frontend |
| `{{BUILD_CMDS}}` | 빌드·테스트 명령 | `./gradlew test`, `npm test`, `npm run build` |
| `{{TEST_DB}}` | 테스트 DB/인프라 | Docker PostgreSQL:5634, Redis:6379 |
| `{{LOCAL_SECRETS}}` | 로컬 시크릿 파일 | application-local.yml(gitignore) |
| `{{AUTONOMOUS_SCOPE}}` | 무중단 모드 범위 | 특정 모듈 또는 전체 |

## 모듈 토큰 (`_module-template/`를 `docs/<module>/`로 복사할 때 채움 — 모듈마다)

| 토큰 | 의미 | 예시 |
|---|---|---|
| `{{MODULE}}` | 모듈명(소문자/경로) | account / board |
| `{{MODULE_UPPER}}` | 에러코드 접두(대문자) | ACCOUNT / BOARD |

## 옵션 토큰 (해당 없으면 절/토큰째 삭제)

| 토큰 | 의미 | 언제 |
|---|---|---|
| `{{DOMAIN_CODES}}` | 상태/권한 코드 체계 | 코드성 상태값을 쓸 때 |
| `{{PROD_DB}}` | 운영 DB 접속 | 관리형/read-only DB일 때(§8) |
| `{{PROD_SECRETS}}` | 운영 시크릿 위치 | 마운트 설정 파일 등 |

## 치환 후 잔재 점검 (범용)
새 프로젝트에 복사·치환하고 **`_module-template/`를 삭제한 뒤**(원본이 남으면 `{{MODULE}}`가 계속 잡힘) 아래를 돌린다:

```bash
# 1) 미치환 토큰 — 0건이어야 한다
grep -rn '{{' .
# 2) 남은 예시 블록 — 각 줄을 보고 "내 스택에 맞으면 유지, 아니면 삭제" 판단
grep -rn '예시(교체)' .
```
- (1)이 0건이면 치환 완료. (2)는 검토 대상(0건 강제는 아님 — 맞는 예시는 남겨도 됨).
- CMS 흔적(원본 스택 단어)은 전부 `<!-- 예시(교체) -->` 블록 안에만 있으니, (2)로 훑어 지우면 된다.
