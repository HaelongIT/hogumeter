# 코드리뷰 20260806 — 30. 교차 관심사

> 리뷰어 D1(모듈 간 계약 드리프트)·D2(스크립트·인프라·CI)의 발견을 통합. 원시 산출물은 `raw/D1-contract.md`·`raw/D2-infra.md`, 반박 검증은 `rebuttal/`.
> **1차 = 정적 검증**이며 범위는 발견까지다 — 수정은 2차.

### X-01 — `check-network-optin.sh`가 주석에만 있는 opt-in 변수 이름을 실제 게이트로 오인한다 · High · ✅수정완료(0638e82)
- **위치**: `scripts/check-network-optin.sh:40-46`(주석 제거 로직, 올바름) / `scripts/check-network-optin.sh:64`(주석 미제거, 결함) / `scripts/check-network-optin.test.sh`(반대 방향 케이스 부재) / `.claude/hooks/guard.sh`(이 시나리오에서 무력함이 설계상 문서화된 두 번째 방어선)
- **근거**: 같은 파일 안에서 `external_urls()`(37-46행)는 `grep -vE '^[[:space:]]*#'`로 주석을 걷어낸 뒤 외부 URL을 찾지만, opt-in 게이트 여부를 판정하는 64행 `grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK' "$target"`은 주석 미제거 원본 파일 전체를 검사한다. 반박 검증(`rebuttal/D2-02.md`)이 임시 디렉토리에서 실제로 재현했다: `# TODO: ALLOW_REAL_ROBOTS 게이트를 아직 안 걸었다` 주석 다음 줄에 게이트 없이 `curl -fsS https://api.telegram.org/...`가 있는 샘플이 `NETWORK OPTIN OK`(exit 0)로 통과했다 — 실제로 게이트된 정상 샘플과 판정이 동일해진다. `guard.sh`는 `bash scripts/x.sh` 내부 호출은 못 본다는 것이 스스로 문서화된 한계(Q-60)이고, `echo '{"tool_input":{"command":"bash scripts/target.sh"}}' | bash .claude/hooks/guard.sh`가 exit 0으로 통과함을 반박 검증에서 실측 — 즉 이 시나리오에서는 방어선이 애초에 `check-network-optin.sh` 하나뿐이고, 그 하나가 뚫린다.
- **영향**: 이 게이트는 저장소에서 "스크립트 내부의 실 네트워크 호출"을 잡는 유일한 방어선이다. 리팩터링 중 실제 opt-in 게이트 호출 줄을 지우면서 설명 주석(다른 스크립트를 언급하거나 TODO)만 남기는 비의도적 실수 시나리오에서 조용히 무력해진다 — 프로젝트 자신의 축적 규칙("정적 검사에서 '이름이 나타난다'와 '실행된다'를 구별하라... 양방향 시험")을 게이트 자신이 위반한다. `check-network-optin.test.sh`는 "주석 속 외부 URL은 실행이 아니다" 방향만 시험하고 "주석 속 opt-in 변수명은 게이트가 아니다" 반대 방향은 시험하지 않아 CI 통과가 반증이 되지 못한다.
- **권고**: 64행의 검사 대상을 `grep -vE '^[[:space:]]*#' "$target" | grep -qE 'ALLOW_REAL_ROBOTS|COLLECTOR_ALLOW_NETWORK'`로 바꿔 `external_urls()`와 같은 규율을 적용한다. 계약 테스트에 "curl 호출은 있지만 opt-in 변수는 주석에만 있다 → 차단" 케이스를 추가한다.
- **출처**: `raw/D2-infra.md` D2-02 · 반박 검증 `rebuttal/D2-02.md` → **CONFIRMED**

### X-02 — `check-table-wiring`·`check-repository-readers`·`check-domain-consumers`의 `open_question()`이 `[해소]`된 Q를 열린 것으로 오판한다 · Medium · ❌미해결
- **위치**: `scripts/check-table-wiring.sh:83-86`, `scripts/check-repository-readers.sh:47-50`, `scripts/check-domain-consumers.sh:54-57`(세 파일이 사실상 동일 함수를 복제) / `scripts/check-dead-columns.sh:92-99`(이미 정확히 고쳐진 자매 함수 `q_open()`) / `scripts/table-wiring-allowlist.txt`·`docs/91-open-questions.md`
- **근거**: 세 게이트는 `grep -qE "^## .*${1}\." "$board"`로 "Q-ID가 `## ` 헤더에 있는가"만 보고 `[열림]`/`[해소 …]` 상태 표식을 구분하지 못한다. `docs/91-open-questions.md`에서 해소된 항목도 헤더가 지워지지 않고 `## [해소 2026-07-25] Q-10. …` 형태로 `## ` 헤더에 그대로 남는다는 것이 원 발견·반박 검증 둘 다에서 확인됐다. `check-dead-columns.sh`의 `q_open()`은 `grep -qE "^#+ \[(열림|부분해소)[^]]*\] ${qid}\b"`로 상태 표식을 정확히 요구해 같은 문제를 이미 정확히 고쳤다. 반박 검증(`rebuttal/D2-01.md`)이 임시 스크립트로 버그판(`open_question`)과 수정판(`q_open`)을 실제 `docs/91-open-questions.md`에 대고 실행해 재현했다: 버그판은 이미 해소된 Q-10·Q-22를 "열려 있다"고 오판하고, 수정판은 정확히 판정한다.
- **영향**: **원 발견은 High로 판정했으나, 반박 검증에서 세 allowlist(`table-wiring-allowlist.txt`·`repository-readers-allowlist.txt`·`domain-consumers-allowlist.txt`) 전수를 대조한 결과 현재 살아 있는 위반 인스턴스는 0건이다** — 유일한 활성 항목(`price_history`/Q-3)는 여전히 `[열림]`인 정당한 면제이고, 나머지 두 allowlist는 활성 행 자체가 없다. `check-board-references.sh`도 "닫힌 Q를 여전히 인용" 케이스는 판정 대상이 아니라고 스스로 명시해 이 결함을 잡지 못한다는 것도 확인됐다. 정규식 결함과 테스트 공백은 실재하지만 **지금 이 순간 거짓 초록을 내는 살아있는 인스턴스가 없어** Medium으로 하향한다 — Q-3(또는 향후 추가되는 다른 Q)가 `[해소]`로 바뀐 뒤에도 allowlist 항목이 안 지워지고 게이트가 계속 GREEN을 내는 순간부터는 즉시 High로 재상향해야 한다.
- **권고**: 세 게이트의 `open_question()`을 `check-dead-columns.sh`의 `q_open()`과 동일한 정규식으로 교체하고, 각 `.test.sh`에 "보드에 있지만 `[해소 …]`인 Q를 인용한 면제는 차단돼야 한다" 케이스를 추가한다. 네 게이트가 같은 함수를 복제해 쓰는 만큼 `scripts/lib/board.sh` 공용 함수로 뽑는 것도 검토(저장소가 이미 `lib/aws-cli.sh`로 같은 패턴을 씀).
- **출처**: `raw/D2-infra.md` D2-01 · 반박 검증 `rebuttal/D2-01.md` → **DOWNGRADED (High→Medium)**

### X-03 — `raw_deal_post.reaction_score`가 collector→DB로는 살아있지만 core 소비처 0, 게이트는 "배선됨"으로 오판 · Medium · ❌미해결
- **위치**: `collector/src/collector/db/raw_deal_sink.py:29-44`(INSERT·UPSERT에 `reaction_score` 포함) / `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPost.java`(엔티티에 `reactionScore` 필드 없음) / `core/src/main/resources/db/migration/V1__init.sql:52`(`reaction_score numeric`) / `docs/90-planning-final.md:58,187`(확정본이 `reaction_score` 노출을 명시) / `scripts/check-dead-columns.sh`·`scripts/dead-columns-allowlist.txt`
- **근거**: collector는 매 폴링마다 `reaction_score`(추천수)를 업서트한다. core 쪽엔 이 컬럼을 읽는 코드가 전혀 없다(`grep -rni reaction core/src web/src`의 유일한 매치는 javadoc 주석뿐). 그런데 확정본은 "반응 신호(reaction_score: 댓글수·추천수 등)를 가능한 만큼 수집"을 요구한다. `bash scripts/check-dead-columns.sh`를 직접 실행하면 `DEAD COLUMNS OK`가 나온다 — 게이트의 `reached()`가 "컬럼 이름이 core/collector/web 셋 중 아무 프로덕션 코드에 나타나는가"만 보기 때문에, collector가 이름을 **쓰는** 코드만으로 "배선됨"으로 오판한다.
- **영향**: collector가 추천수를 계속 수집해 DB엔 정직하게 쌓이지만, core가 한 번도 읽지 않아 사용자는 화면에서 "반응 신호"를 영원히 볼 수 없다. 데이터 유실은 아니지만 확정본이 요구한 기능이 조용히 빠진 채로 아무 게이트도, `docs/91`의 어떤 열린 항목도 이 사실을 추적하지 않는다.
- **권고**: (a) `docs/91`에 새 Q를 열어 "reaction_score 미노출"을 명시하거나, (b) 확정본 요구를 포기하기로 결정했다면 `decision-log.md`에 기록하고 `docs/90`에 델타를 남긴다. 게이트 쪽 근본 수정(collector 쓰기 vs core 읽기를 분리해 "절반만 배선"을 구별)은 이번 리뷰 범위를 넘는 리팩터라 별도 작업으로 제안만 남긴다.
- **출처**: `raw/D1-contract.md` D1-01 (반박 검증 미실시, 원 심각도 유지)

### X-04 — `check-ci-coverage.sh`의 1단 닫힘 판정이 호출자 스크립트 안의 주석을 실행으로 센다 · Medium · ❌미해결
- **위치**: `scripts/check-ci-coverage.sh:40`(ci.yml 자체는 주석 제거) / `scripts/check-ci-coverage.sh:54-61`(`called_by_ci_script()`, 호출자 스크립트는 주석 미제거) / `scripts/check-ci-coverage.test.sh:41-44`(ci.yml 주석화 케이스만 있고 1단 닫힘 방향은 없음)
- **근거**: `ci.yml`에서 직접 호출을 찾을 때는 `grep -vE '^[[:space:]]*#'`로 주석을 걸러낸 뒤 검색해 "주석은 실행이 아니다"를 지키지만, `backup-drill.sh`처럼 ci.yml이 직접 안 부르고 중간 스크립트가 대신 부르는 1단 닫힘을 판정하는 `called_by_ci_script()`는 `$caller` 파일 전체를 원본 그대로 `grep -qF`한다 — 같은 파일 안에서 두 판정 함수가 "주석은 실행이 아니다"를 다르게 적용하는 비대칭이 있다.
- **영향**: 누군가 `backup-drill.sh`의 `bash scripts/restore-drill.sh "$dump"` 실행 줄을 지우거나 주석 처리하면서 설명 주석(예: "restore-drill.sh는 여기서 불렸었다")만 남기면, `called_by_ci_script("restore-drill.sh")`는 여전히 참을 반환해 이 게이트가 "다른 드릴을 통해 커버됨"으로 통과시킨다. 게이트의 존재 이유("드릴이 CI에서 실제로 실행되는가")가 정확히 무력화되는 경로인데, 직접 호출 경로의 동일한 취약점만 테스트돼 있고 1단 닫힘 경로는 시험도 방어도 없다.
- **권고**: `called_by_ci_script()`의 검사 대상도 `grep -vE '^[[:space:]]*#' "$root/$caller" | grep -qF "$name"`로 바꾼다. 계약 테스트에 "backup-drill.sh 안에서 restore-drill.sh 호출 줄만 주석 처리 → 차단" 케이스를 추가한다.
- **출처**: `raw/D2-infra.md` D2-03 (반박 검증 미실시, 원 심각도 유지)

### X-05 — `price_history` allowlist 2곳이 Q-3의 죽은 재개 트리거("키 발급 시")를 그대로 인용 · Medium · ❌미해결
- **위치**: `scripts/dead-columns-allowlist.txt`(`price_history.fetched_at Q-3 …키 발급 시 배선`) / `scripts/table-wiring-allowlist.txt`(`price_history Q-3 네이버 쇼핑 API 키 미발급 - 현재가 수집기가 없다`) / `docs/91-open-questions.md`(Q-3 본문) / `working-area/decisions-needed.md`(D-7)
- **근거**: 두 allowlist 항목은 재개 조건을 "키 발급"으로 서술하지만, Q-3 본문(2026-07-24 갱신)은 네이버 개발자센터가 2026-07-31부로 전면 종료를 공지해 "'재개 트리거 = 키 발급'이라는 이전 잠정값이 통째로 무의미해졌다"고 명시하고, "재개 트리거를 새로 정하지 않는다 — 사람이 정할 사안으로 승격"한다고 못박는다. D-7도 "'키 발급 대기'는 더 이상 유효한 계획이 아니다"라고 재확인한다. `check-dead-columns.sh`·`check-table-wiring.sh`는 인용된 Q-ID가 `[열림]`인지만 보고 트리거 문구는 읽지 않으므로(직접 실행 확인: 둘 다 GREEN) 이 드리프트를 구조적으로 못 잡는다 — 세 게이트 모두 명시된 한계 그대로다.
- **영향**: 다음 세션이 allowlist만 보고 "네이버 키만 받으면 이 면제가 풀리겠다"고 오판해 실제로 키를 발급받아도, 서비스가 이미 종료돼(오늘 기준 종료일 2026-07-31 지남) 아무것도 안 풀리는 헛수고를 하게 된다. 진짜 다음 행동(대체 데이터 소스 결정, D-7)이 allowlist에서는 안 보인다.
- **권고**: 두 allowlist 줄의 사유를 "네이버 쇼핑 API 2026-07-31 서비스 종료(대체 안 됨) — 재개는 D-7 확정 후"로 갱신한다. Q-ID는 Q-3 그대로 유지(여전히 열려 있고 인용도 유효), 사유 문구만 최신화.
- **출처**: `raw/D1-contract.md` D1-02 (반박 검증 미실시, 원 심각도 유지)

### X-06 — 저장소에 정적분석 도구가 하나도 없다 · Medium · ◐부분반영(web 완료 — `docs/91` Q-90, collector·core는 추적 중)
- **위치**: `core/build.gradle.kts`(spotbugs/errorprone/checkstyle/pmd/nullaway/jacoco 전무), `collector/pyproject.toml`(ruff/mypy/black 전무), `web/package.json`(eslint/prettier 전무)
- **근거**: 세 파일을 직접 열어 확인했다. `core/build.gradle.kts`의 `plugins {}`·`dependencies {}`는 `java`·Spring Boot·dependency-management뿐이고 정적분석 플러그인이 없다. `collector/pyproject.toml`의 `[dependency-groups].dev`는 `pytest`·`testcontainers[postgres]`뿐이다. `web/package.json`의 `devDependencies`에도 `eslint`·`prettier` 계열이 없다 — 있는 건 `typescript`(빌드 스크립트가 `"build": "tsc --noEmit && vite build"`로 타입체크만 겸함)와 `vitest`뿐이다. 저장소 전체에서 기계적 정적 검증은 CI `lint` 잡의 shellcheck(`--severity=warning`, `scripts/**` 대상)와 web 빌드에 끼워진 `tsc --noEmit` 둘뿐이다(둘 다 이번 리뷰의 D2·C3 원 발견에서 직접 실행 확인됨).
- **영향**: 이번 리뷰가 찾은 numbered 발견 13건(C3 4건 + D1 5건 + D2 4건) 중, 표준·거의-표준 린트/타입 도구가 기계적으로 잡았을 유형인지 하나씩 판별했다. **2건(15%)** — FE-02(`PurchasePanel`)·FE-03(`UsedComparisonPage`)의 "fetch effect에 취소/레이스 가드 누락" 패턴 — 이 web 훅 관련 린트 규칙(현재는 `eslint` 자체가 없어 `react-hooks/exhaustive-deps` 같은 규칙도 전혀 안 돎; `UsedComparisonPage.tsx`에 이미 이 규칙을 겨냥한 `eslint-disable-next-line react-hooks/exhaustive-deps` 주석이 있다는 사실 자체가 "린트가 있었으면 최소 이 effect를 주목했을 것"이라는 정황이다)로 사전에 표면화됐을 가능성이 있다. 나머지 11건(FE-01·FE-04, D1 5건 전부, D2 4건 전부)은 크로스-언어 DB 계약 드리프트, 문서-코드 트리거 불일치, 셸 정규식의 의미 오류, React 상태 전이 순서 버그 등으로 — spotbugs/checkstyle류·mypy/ruff류·eslint류 어느 것으로도 기계적으로 잡히는 유형이 아니었다(shellcheck는 D2의 4건이 걸린 파일들에 이미 돌고 있었는데도 못 잡았다 — 문법은 유효하고 의미만 틀렸기 때문). 즉 이번 리뷰에서 사람이 잡은 결함의 대다수는 크로스 모듈 계약·도메인 로직 이해가 필요해 도구 도입만으로는 못 막았을 것이나, web 쪽 훅 취소 가드 패턴은 반복돼 온 결함 유형(C3 원 발견 자체가 "AlertPolicyPanel 등 5개 파일은 지키는데 2개 파일만 빠뜨렸다"는 산발적 누락을 지적)이라 기계 강제의 이득이 뚜렷하다.
- **권고**: 도입 우선순위는 (1) **web: eslint + eslint-plugin-react-hooks** — 이번 리뷰의 실측 유일한 적중 사례(2/13)가 여기 있고, "5개 파일은 패턴을 지키는데 2개는 빠뜨렸다"는 산발적 누락은 정확히 린트가 강제하는 일관성 문제다. (2) **collector: ruff + mypy** — `.claude/rules/collector-python.md`에 누적된 교훈(파서 침묵 실패, 상태값 허용집합, 라운드 코드 유형 등)이 이미 여럿 동적 타입 관련이라 향후 회귀 예방 기대값이 크지만, 이번 리뷰 범위(D1)에선 실측 사례가 없었다. (3) **core: checkstyle/spotbugs(+nullaway)** — Boot 4/Jackson 3 이관처럼 컴파일은 되지만 런타임에만 드러나는 함정이 `.claude/rules/core-java.md`에 누적돼 있어 장기적으로 유효할 수 있으나, 이번 리뷰에서 core 쪽 정적 결함 실측은 없어 우선순위는 가장 낮다.
- **출처**: 통합 담당(F2) 직접 확인 — `core/build.gradle.kts`·`collector/pyproject.toml`·`web/package.json` 원문 대조, `raw/C3-web.md`·`raw/D1-contract.md`·`raw/D2-infra.md` 전수 재검토

### X-07 — `RawDealPost.java` 클래스 javadoc이 실제로 매핑된 컬럼을 "미매핑"이라 오기 · Low · ❌미해결
- **위치**: `core/src/main/java/dev/hogumeter/core/adapter/persistence/RawDealPost.java:13`(javadoc) / 같은 파일 36-40행(`headline_price`·`posted_at` 실제 매핑)
- **근거**: javadoc은 `headline_price`·`posted_at`도 "이 슬라이스에서 미매핑"이라 적었지만, 바로 아래 필드 선언에서 둘 다 `@Column`으로 매핑돼 있고 `getHeadlinePrice()`·`getPostedAt()`가 실제 도메인 로직(`IngestDealsUseCase`의 널 가드·`firstSeen` 계산)에 쓰인다. 진짜 미매핑은 `body_text`·`reaction_score`·`raw` 셋뿐이다.
- **영향**: 코드 동작엔 영향 없다(문서만 틀림). 다만 이 주석을 믿고 "headline_price/posted_at은 core가 못 읽으니 네이티브 SQL로 다뤄야 한다"고 오판하거나, 진짜 미매핑인 `reaction_score`(X-03)가 매핑된 두 컬럼과 한 목록에 섞여 있어 "이것도 곧 매핑되겠거니" 하고 실제 미배선 상태를 놓치기 쉽다.
- **권고**: javadoc을 "미매핑: body_text·reaction_score·raw jsonb"로 정정.
- **출처**: `raw/D1-contract.md` D1-03 (반박 검증 미실시, 원 심각도 유지)

### X-08 — `repository-readers-allowlist.txt`의 안내 주석이 현재 파일 상태와 안 맞음 · Low · ❌미해결
- **위치**: `scripts/repository-readers-allowlist.txt`(마지막 줄 "테스트는 호출자가 아니다. 두 메서드 다 테스트에서만 불린다.") / 파일 본문(실제 데이터 행 0개)
- **근거**: 주석은 "두 메서드"가 면제 대상인 것처럼 말하지만 파일에 실제 `<Repository>.<method>` 행이 하나도 없다. `bash scripts/check-repository-readers.sh` 직접 실행 결과 `REPOSITORY READERS OK: 조회 메서드 36개 (호출됨 36 · 미사용 선언 0)` — 예전에 있었던 2개 항목이 이미 배선되며 지워졌는데 마지막 안내 문장만 안 지워진 것으로 보인다.
- **영향**: 게이트 동작엔 영향 없음(주석 줄은 파싱에서 스킵된다). 사람이 파일을 훑을 때만 혼란(있지도 않은 "두 메서드"를 찾게 됨).
- **권고**: 마지막 줄을 지우거나 "현재 미사용 선언 0건"으로 갱신.
- **출처**: `raw/D1-contract.md` D1-05 (반박 검증 미실시, 원 심각도 유지)

### X-09 — `smoke.sh`의 SEC-02 인증 검증용 임시 컨테이너가 compose 프로젝트 밖에 있어 조기 실패 시 정리되지 않을 수 있다 · Low · ❌미해결
- **위치**: `scripts/smoke.sh:1196-1211`
- **근거**: 전역 `trap cleanup EXIT`(37-41행)는 `compose -p "$PROJECT" down -v --remove-orphans`만 수행해, `docker run`으로 직접 띄운 `auth_cid` 컨테이너는 정리 대상이 아니다. `docker rm -f "$auth_cid"`가 1211행에 명시적으로 있지만, 그 사이(1196~1210행)에 `set -e`로 스크립트가 조기 종료되면(고정 `sleep 2` 안에 nginx가 안 뜬 상태에서 curl이 connection-refused를 내는 경우 등) 그 줄에 도달하지 못한다.
- **영향**: CI 러너에서는 러너 자체가 매 잡마다 새로 프로비저닝되므로 실질 피해가 작지만, 로컬에서 `bash scripts/smoke.sh`를 반복 실행하는 개발 환경에서는 고아 컨테이너가 `AUTH_PORT`(기본 54000)를 계속 점유해 다음 실행이 포트 바인딩 실패로 깨질 수 있다.
- **권고**: `auth_cid` 생성 직후 `trap '[ -n "${auth_cid:-}" ] && docker rm -f "$auth_cid" >/dev/null 2>&1; cleanup' EXIT` 형태로 기존 trap을 감싸거나, `sleep 2` 대신 `/healthz` 폴링 루프로 바꿔 실패를 `fail()`로 흡수한다.
- **출처**: `raw/D2-infra.md` D2-04 (반박 검증 미실시, 원 심각도 유지)

### X-10 — `used_listing_observation.raw`도 같은 게이트 사각지대를 통과(의도된 설계, allowlist 미선언) · Info · ❌미해결
- **위치**: `collector/src/collector/db/used_listing_sink.py:69` / `core/src/main/java/dev/hogumeter/core/adapter/persistence/UsedListingObservationEntity.java:16`("raw는 core가 안 읽는다"는 설계 의도 명시) / `scripts/dead-columns-allowlist.txt`(이 컬럼 미선언)
- **근거**: 엔티티 자체가 "raw는 core가 안 읽는다"고 설계 의도로 명시해 X-03(reaction_score)과 달리 버그가 아니라 의도된 "크롤링 원본 보관 전용" 패턴이다. 다만 `dead-columns-allowlist.txt`에 이 컬럼이 선언돼 있지 않아, `check-dead-columns.sh`가 X-03과 동일한 메커니즘(collector가 쓰는 코드 안에 이름이 나타남)으로 우연히 "배선됨" 판정을 내려 통과시킨다(직접 실행 확인: `DEAD COLUMNS OK`).
- **영향**: 지금 당장은 문제 없다. 다만 이 컬럼이 정말 죽었는지/설계인지 판단할 근거가 코드 주석 하나뿐이라, `dead-columns-allowlist.txt`의 INTENTIONAL 패턴(`deal_event.base_price` 등)처럼 명시적으로 선언해 두지 않으면 다음 감사가 "게이트가 통과시켰으니 배선됐다"고 잘못 믿을 위험이 있다.
- **권고**: `used_listing_observation.raw INTENTIONAL 크롤링 원본 보관 전용, core는 읽기만 하는 테이블이라 설계상 미매핑`을 `dead-columns-allowlist.txt`에 추가해 "우연히 통과"를 "선언적으로 면제"로 바꾼다. 급하지 않음.
- **출처**: `raw/D1-contract.md` D1-04 (반박 검증 미실시, 원 심각도 유지)

## 검토했으나 문제없음(통합)
- **core↔collector DB 계약 자체**(`raw_deal_post`·`used_listing_observation`·`site_poll_state`·`used_search`·`alias_dictionary`)는 컬럼 단위 대조표 기준 실질 불일치 없음 — X-03·X-10(미소비/게이트 사각지대)을 제외하면 나머지 전 컬럼이 정합.
- `raw_deal_post` 상태 허용집합: collector `_VALID_STATUS` = DB CHECK = core `DealStatus.ENDED_RAW_STATUSES`(서브셋 축약, 의도된 설계) — 정확히 일치.
- `origin` 허용집합(`LIVE`/`BACKFILL`): `raw_deal_post.origin`·`deal_event.origin` CHECK와 core `Origin` enum이 완전히 동일, `Origin.valueOf(...)` 예외 없이 왕복.
- `SHIPPING_UNKNOWN`·`FREE_PRICE` 문자열 표식: `scripts/check-tag-contract.sh` 직접 실행 → `TAG CONTRACT OK`. collector `price.py`/core `DealTags.java`/web `present.ts` 세 사본이 실제로 일치.
- `scripts/check-source-vocabulary.sh` 직접 실행 → `SOURCE VOCABULARY OK`(파서 4개 — 신품 3/중고선언 1).
- SEC-05 크기 상한(`MAX_TITLE`·`MAX_URL`·`MAX_POST_ID`·`MAX_RAW_BYTES`)은 DB 타입이 무제한이라 "불일치"처럼 보이나, collector가 유일한 쓰기 주체이고 초과 시 적재 자체를 안 하므로 실제로는 계약 드리프트가 아니라 단방향 자기 규율.
- migrations 실행 순서: `collector/tests/conftest.py`가 `core/src/main/resources/db/migration`을 직접 가리켜 실제 Flyway SQL을 그대로 테스트 컨테이너에 적용(정적 미러 사본이 아니라 원본 그대로 사용).
- `backup.sh`의 `trap 'rm -f "$out"' ERR` + `set -o pipefail`: `pg_dump | gzip` 파이프 실패 시 부분 파일이 정확히 지워짐 — 의도대로 동작.
- 드릴 격리(`restore-drill.sh`·`rollback-drill.sh`·`offsite-drill.sh`·`backup-drill.sh`): 전부 전용 컨테이너·네트워크·`--tmpfs`·전용 포트/디렉토리 + `trap cleanup EXIT`로 운영/개발 compose·볼륨에 닿는 경로 없음.
- `smoke.sh`의 상태 판정: 헬스체크·DB 장애 주입·재시작 정책·포트 바인딩·인증 온오프까지 `docker inspect`의 구조화 필드나 HTTP 상태코드로 판정(로그 문구 grep 아님).
- CI `secrets` 잡: `fetch-depth: 0` + `gitleaks detect --source=/repo`로 히스토리 전체를 봄 — shallow clone으로 무력화되지 않음.
- Dockerfile 3종(core/web/collector) 전부 구체 버전 태그 고정, 멀티스테이지가 빌드 산출물만 최종 스테이지로 복사.
- `docker-compose.yml`의 조건부 환경변수(`TELEGRAM_ENABLED` 등)는 전부 비지 않은 기본값을 쓰고 `check-conditional-props.sh`가 이 계약을 실제로 강제.

## 리뷰어별 원시 산출물
- `raw/D1-contract.md` — D1-01~05(Medium 2 / Low 2 / Info 1 — 항목별 헤더 기준. 파일 상단 요약줄은 "Medium 3 / Low 2 / Info 2"로 적혀 있으나 실제 5개 항목 헤더와 맞지 않아, 헤더 실측값을 썼다)
- `raw/D2-infra.md` — D2-01~04(High 2 / Medium 1 / Low 1)
