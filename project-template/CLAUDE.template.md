<!--
  CLAUDE.template.md — AI 개발 진입점 템플릿 (솔로 개발자판)
  ─ 이 파일을 새 프로젝트 루트에 `CLAUDE.md`로 두고 {{...}} 플레이스홀더를 치환한다.
  ─ 정본 배포 레이아웃(반드시 이대로 — 이 파일의 경로 참조가 이 구조를 전제한다):
        <프로젝트 루트>/
          CLAUDE.md            ← 이 파일(리네임 후)
          docs/                ← 00~05·90·lessons·<module>/
          working-area/        ← docs와 형제(루트 직속). docs/ 밑에 넣지 말 것.
  ─ 플레이스홀더 전체 목록·치환법 = PLACEHOLDERS.md
  ─ <!-- 예시(교체) --> 블록은 현 스택(Spring+React) 샘플이다. 맞지 않으면 지워라.
  ─ 왜 이 항목들인지 = README.md
  ─ 셋업 끝나면 이 주석 블록을 지운다.
-->

# CLAUDE.md — {{PROJECT_NAME}} 개발 가이드 (AI 진입점)

이 파일은 AI 에이전트가 매 세션 시작 시 읽는 진입점이다. **작업 전 반드시 관련 `docs/` 문서를 읽고**, 아래 원칙(특히 TDD·메모리·기록 규칙)을 지킨다.

---

## 1. 프로젝트 한 줄 정의

{{PROJECT_ONE_LINER}}

**기술 스택**
- 백엔드: {{BACKEND_STACK}} <!-- 예시(교체): Spring Boot + MyBatis -->
- 프론트엔드: {{FRONTEND_STACK}} <!-- 예시(교체): React(CSR) + Vite + Zustand + Ant Design -->
- DB: {{DB}} <!-- 예시(교체): PostgreSQL 단일 인스턴스 -->
- 인프라/외부의존: {{INFRA}} <!-- 예시(교체): Redis(세션/토큰), SMTP(메일) -->
- 배포: {{DEPLOY}} <!-- 예시(교체): 단일 인스턴스, Nginx same-origin 리버스프록시 -->

> **현재 개발 대상 = {{CURRENT_MODULE}}.** 기획·인수조건·데이터모델·에러는 `docs/{{CURRENT_MODULE}}/00~07`이 정본. 최신 상태·한계는 항상 코드·git·`docs/90-open-items.md`가 정본.

---

## 2. 문서 인덱스 — 작업 전 읽을 것

| 문서 | 내용 |
|------|------|
| `docs/00-project-overview.md` | 전체 개요 + 모듈 지도 + 표준 요청 흐름 |
| `docs/01-architecture.md` | 런타임 아키텍처·레이어·디렉토리 구조(feature-first) |
| `docs/02-conventions.md` | API·응답포맷·네이밍·도메인 타입·날짜·**코딩 컨벤션·용어집** |
| `docs/03-error-handling.md` | 에러코드 체계 + refactor seam + 카탈로그 |
| `docs/04-non-functional.md` | 비기능(보안·성능·가용성·로깅·테스트가능성) |
| `docs/05-tdd-guide.md` | **TDD 워크플로우·테스트 전략·모듈화·메모리 규칙** |
| `docs/<module>/00~07` | 모듈 상세(개요·아키텍처·데이터모델·API·인수조건·NFR·TDD·에러) |
| `docs/90-open-items.md` | **보류 보드 — 열린 한계/미결. 임의 확정 금지, 잠정값+재개 트리거** |
| `docs/lessons-learned.md` | TDD·버그·리팩토링 교훈 누적(세션 시작 시 필독) |
| `working-area/` | **개인 작업 보드** — `decisions-needed`(정할 것)·`decision-log`(정한 것)·`pre-deploy-checklist`(운영 갭)·`schema-change-queue`(DB 변경 큐)·`review-template`(리뷰) |

---

## 3. 작업 원칙

- **TDD 필수.** 모든 기능은 실패 테스트 → 최소 구현 → 리팩토링(Red-Green-Refactor). 절차는 `docs/05`.
- **인수조건이 곧 테스트.** 각 모듈 문서의 Given-When-Then 인수조건·테스트 케이스 목록을 테스트로 1:1 변환한 뒤 구현한다.
- **모듈 경계 준수(feature-first).** 패키지는 도메인 우선. 비즈니스 로직은 Service에, DB·외부(메일 등)는 인터페이스로 주입해 단위 테스트에서 목으로 대체 가능하게. 상세 `docs/01`.
- **생성자 주입 + 부수효과 추상화.** 시각(`Clock`)·랜덤/토큰 생성기·메일/알림 등 부수효과는 주입으로 분리해 테스트에서 결정적으로 대체한다.
- **한계·보류는 즉시 기록 (단일 보드 = `docs/90`).** 작업 중 한계·미구현·임시방편·추후과제·미해결 결정이 생기면 **그 자리에서 `docs/90`에 항목으로 적는다.** "한계가 있다"고 말로만 끝내지 않는다. **매 세션 시작 시 `docs/90`을 읽어** 열린 항목을 인식한다. 보류가 해소되면 `working-area/decision-log.md`로 옮긴다.
- **결정이 필요한 것은 즉시 등록 (`working-area/decisions-needed.md`).** "이건 사람이 정해야 한다"는 열린 질문(정책·보안·제품·되돌리기 어려운 선택)이 생기면 그 자리에서 적는다(질문·선택지 장단·잠정값·영향). 임의 확정하지 않는다. 정해지면 `decision-log.md`로 옮긴다.
- **운영 전용 설정은 즉시 배포전 체크리스트에 (`working-area/pre-deploy-checklist.md`).** 로컬/테스트에선 되지만 **운영 배포 시 별도 설정·적용이 필요한 것**(시크릿·env, DB DDL, 인프라 의존성, CORS·도메인, 파일 경로/권한 등)이 드러나면 그 자리에서 `[필수]/[권장]`으로 추가한다. 이 파일이 go-live 단일 점검 지점.
- **refactor seam을 지킨다.** 미확정·잠정 결정은 "한 곳만 바꾸면 되는" 지점(인터페이스·상수·설정)에 격리해 나중에 최소 변경으로 뒤집을 수 있게 한다.
- **문서가 정답은 아니다.** 비합리적이거나 더 나은 방식이 보이면 임의로 바꾸지 말고 근거·대안을 제시해 확인받는다(단 §7 autonomous 모드 대상은 자율 반영+기록).

> **loose-end 라우팅(핵심 규율):** 열린 것은 종류별로 정확히 **한 보드**에 즉시 기록한다 — 정할 것=`decisions-needed` / 잠정진행 보류=`docs/90` / 운영 갭=`pre-deploy-checklist` / 정한 것=`decision-log` / 재사용 교훈=`lessons-learned`. **말로 끝내지 않는다.**

---

## 4. 메모리 규칙 — 교훈·트러블슈팅 정리 (중요)

**TDD·버그 수정·리팩토링·트러블슈팅**에서 얻은 **재사용 가능한 교훈**을 `docs/lessons-learned.md`에 누적한다. 매 세션 시작 시 먼저 읽어 같은 실수를 반복하지 않는다.

- **기록 대상**: 테스트 실패의 비자명한 원인·해결책 / 버그·리팩토링에서 드러난 결함(**원인→해결 명시**) / 반복 픽스처 패턴 / 명세의 빈틈 / 라이브러리·버전 함정.
- **기록 금지**: 한 번만 쓰이는 정보, 코드에 이미 드러나는 사실, 일반 상식.
- **형식**: 날짜 / 맥락 / 교훈(원인→해결) / 적용 방법. append 타이밍 = 매 사이클·버그수정·리팩토링 직후. "고쳤다"로 끝내지 않고 재사용 가치 있으면 남긴다.

---

## 5. Git & 커밋 규칙

{{REPO_LAYOUT}}
<!-- 예시(교체): 저장소가 여럿이면(예: 루트 docs / backend / frontend) 각각 별도 원격.
     최상위는 .gitignore로 하위 코드 repo를 추적 제외(중첩 추적 금지). -->

- **커밋 타이밍: 작업(feature) 단위가 끝날 때마다.** 변경된 저장소만 커밋. **테스트 GREEN 확인 후** 커밋.
- **푸시는 사용자 통제.** 에이전트는 커밋까지만. `main`에 강제 push·임의 브랜치 전환/머지 금지 — 사용자가 정한 브랜치·타이밍을 따른다.
- 커밋 메시지: 무엇을·왜. 관련 항목ID/보류번호 교차링크.
- **`.gitignore` 필수 항목**: 로컬 시크릿({{LOCAL_SECRETS}})·빌드 산출물·의존성 디렉토리·`.env`. 다중 repo면 최상위에서 하위 코드 repo를 추적 제외.
  <!-- 예시(교체): application-local.yml / build/ dist/ node_modules/ .env* / cms_backend/ cms_frontend/ -->


---

## 6. 빌드·테스트 명령

```bash
{{BUILD_CMDS}}
```
<!-- 예시(교체):
# 백엔드
./gradlew test        # 단위·통합 테스트
./gradlew bootRun     # 로컬 실행
# 프론트엔드
npm test              # 단위 테스트(변경 시 GREEN 확인 후 커밋)
npm run build         # 타입체크 + 빌드
-->

- 테스트 DB/인프라: {{TEST_DB}} <!-- 예시(교체): Docker PostgreSQL localhost:5634, Redis localhost:6379 -->
- 로컬 시크릿 위치: {{LOCAL_SECRETS}} <!-- 예시(교체): src/main/resources/application-local.yml (gitignore) -->

---

## 7. Autonomous(무중단) 모드 — 범위 한정

> **적용 범위:** {{AUTONOMOUS_SCOPE}}에 한정. <!-- 예시(교체): 특정 모듈(board 등) 또는 전체 -->

**무중단은 "구현 실행"의 자율이다 — "기획/설계 결정"은 대상이 아니다.** plan 승인 후 코드를 쌓는 자율이며, 데이터 모델·정책·표현 방식 같은 **기획 선택은 임의 확정하지 말고 묻는다.** 사소·되돌릴 수 있는 것만 잠정값+기록으로 진행한다.

- **멈추지 않는 것**: 기능 단위 커밋 자율 / 잠정 기본값으로 진행(결정필요는 `decisions-needed`에 기록만) / 테스트 DB로 완주.
- **그래도 지키는 것**: 테스트 GREEN 후에만 커밋 / 되돌리기 어려운 것(데이터 파기·외부 공개·비용 발생·보안 정책)만 정지하고 묻는다 / 기록 의무 유지(`docs/90`·`decisions-needed`·`lessons-learned`).

---

## 8. (옵션) 관리형/원격 DB 규칙

<!-- 운영 DB를 직접 못 만지는 환경(DBA 소관·read-only)일 때만 사용. 아니면 이 절 삭제. -->
- 운영 DB({{PROD_DB}})는 **read-only 조회만**(`SELECT`/`information_schema`). INSERT/UPDATE/DELETE/DDL 금지.
- 스키마 변경이 필요하면 운영 DB를 건드리지 말고 **`working-area/schema-change-queue.md`에 DDL을 모으고**, 동시에 **로컬 테스트 DB 스키마에 같은 변경을 반영**해 테스트가 돌게 한다. 운영 반영은 DB 담당자 몫.
