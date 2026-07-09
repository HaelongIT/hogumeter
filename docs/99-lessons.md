# 99. 교훈 누적 (Lessons)

> TDD 사이클·디버깅·스파이크에서 얻은 **재사용 가능한 교훈**을 즉시 append한다(CLAUDE.md 교훈 축적 프로토콜).
> 반복 적용 가능한 규칙이 되면 CLAUDE.md `## 축적된 규칙`으로 한 줄 승격. 매 세션 시작 시 읽는다.
>
> **기록 대상**: 테스트 실패의 비자명한 원인·해결 / 버그·리팩토링에서 드러난 결함(원인→해결) / 반복 픽스처 패턴 / 명세의 빈틈 / 라이브러리·버전 함정.
> **기록 금지**: 한 번만 쓰이는 정보, 코드에 이미 드러나는 사실, 일반 상식.

## 항목 양식

```
### <날짜> <제목>
- 맥락:
- 증상:
- 원인:
- 규칙화된 교훈 (원인→해결):
- 관련 테스트:
```

---

### 2026-07-04 Spring Boot 4.1 이관 — 모듈형 스타터 + 전이 의존 버전 함정
- 맥락: D-2 결정으로 core를 Boot 3.5.16 → 4.1.0 이관(스캐폴드 저표면적 시점). `./gradlew test`로 검증.
- 증상: (1) `org.testcontainers:junit-jupiter`/`postgresql` 버전 미해석(`Could not find ... :`) → 컴파일 실패. (2) 그 후 런타임 `FlywayException: Unsupported Database: PostgreSQL 16.14`. (3) `PostgreSQLContainer<?>` 컴파일 에러 `does not take parameters`.
- 원인: Boot 4의 **모듈형 스타터** + BOM이 끌어온 **Testcontainers 2.0 파괴적 변경**. ① `spring-boot-starter-flyway`는 flyway-core만 제공 — PostgreSQL 방언 모듈(`flyway-database-postgresql`)은 여전히 별도 명시 필요. ② Testcontainers 2.0에서 아티팩트가 `testcontainers-` 접두사로 리네임(`junit-jupiter`→`testcontainers-junit-jupiter`, `postgresql`→`testcontainers-postgresql`), 클래스가 모듈별 패키지로 이동(`org.testcontainers.containers.PostgreSQLContainer`→`org.testcontainers.postgresql.PostgreSQLContainer`), self-type 재귀 제네릭 제거(비제네릭 클래스).
- 규칙화된 교훈 (원인→해결): **Boot 메이저 이관 시 "starter로 교체"는 방언/DB 전용 모듈까지 옮겨주지 않는다 — 런타임 GREEN까지 확인**. BOM이 major-bump한 전이 라이브러리(Testcontainers 2.0)의 아티팩트 좌표·패키지·제네릭 시그니처 변경을 함께 반영. `web`→`webmvc`도 Boot 4 리네임. 4.0.x는 2026-12 EOL이므로 EOL 회피가 목적이면 4.1.x 채택.
- 관련 테스트: `FlywayMigrationTest`, `CoreApplicationTests.contextLoads()` (둘 다 Testcontainers postgres:16, `@ServiceConnection`).

### 2026-07-04 BM-06 자동확장 — "실제 표본이 늘 때만" 확장 표기해야 경계 테스트가 격리된다
- 맥락: BM-06 AC-5 자동확장(기간 P 내 표본 부족 시 과거로 윈도우 확장) + AC-7 경계 스윕(n×K 조합으로 tier 판정)을 함께 구현.
- 증상: 확장을 "유효 개월이 periodMonths 초과하면 무조건 expandedToMonths 세팅"으로 짜면, 과거 딜이 없는데도(n<K_FILL이기만 하면) 루프가 상한까지 돌며 expandedToMonths=상한을 기록 → 경계 테스트의 tier 격리가 깨진다.
- 원인: 확장의 관측 가능한 산출물(expandedToMonths)을 "탐색한 윈도우"가 아니라 "실제 데이터 span"으로 정의하지 않으면, no-op(딜 안 늘어남)과 실제 확장이 구분되지 않는다.
- 규칙화된 교훈 (원인→해결): **윈도우 확장 로직은 "표본이 실제로 증가했을 때만" 유효 범위·표기를 갱신**하라(`wider.size() > sample.size()` 가드). 그래야 "과거 딜 없음 → 확장 무발동(null)"이 성립해 경계 테스트가 tier만 순수 격리한다. 상한(12개월) 밖 딜은 어떤 경우도 미포함. → BM-04 병합 시간 윈도우·BM-05 표본 윈도우에도 동일 적용.
- 관련 테스트: `BenchmarkCalculatorTest.autoExpandsPeriodUntilKFillReached / doesNotPullDealsBeyondTwelveMonthCap / noExpansionWhenNoOlderDealsExist`, `BenchmarkCalculatorBoundaryTest`.

### 2026-07-04 collector venv 콘솔 스크립트 stale — `uv run pytest`가 깨진 python 경로로 exec 실패
- 맥락: BM-02 착수 시 `uv run pytest` 실행이 `.venv/bin/python: cannot execute`로 실패(shebang 경로에 공백 `hogumeter /collector`). 실제 디렉토리는 공백 없음(od로 확인).
- 원인: `.venv` 콘솔 스크립트가 옛 프로젝트 경로(끝 공백, D-1)로 baked된 stale 상태. 디렉토리 공백 제거 후에도 스크립트는 갱신 안 됨. `.venv`는 gitignore라 재생성 가능.
- 규칙화된 교훈 (원인→해결): collector 로컬 테스트가 shebang/경로 문제로 깨지면 **`uv sync --reinstall`로 venv 재생성**(스크립트가 현재 경로로 재baked) 또는 **`uv run python -m pytest`로 우회**(콘솔 스크립트 안 거침). CI는 clean checkout이라 무관.
- 관련 테스트: `collector/tests/test_price.py` (uv run pytest 10 passed).

### 2026-07-08 Boot 4 슬라이스 테스트 — 별도 스타터 + autoconfig 패키지 이동
- 맥락: 등록 REST 통합 테스트에 `@AutoConfigureMockMvc` 사용 시 `package org.springframework.boot.test.autoconfigure.web.servlet does not exist` 컴파일 실패.
- 원인: Boot 4는 슬라이스 테스트 자동설정을 **모듈별 스타터**로 분리(`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test` 등)하고, 클래스 패키지를 `org.springframework.boot.test.autoconfigure.web.servlet` → **`org.springframework.boot.webmvc.test.autoconfigure`**로 이동(런타임 autoconfig도 `org.springframework.boot.<module>.autoconfigure`로 재편 — Flyway·JPA 동일 패턴). gradle 캐시에 남은 3.5.x jar의 옛 경로에 낚이지 않도록 실제 4.x jar를 `unzip -l`로 확인.
- 규칙화된 교훈 (원인→해결): Boot 4에서 `@WebMvcTest`/`@AutoConfigureMockMvc` 등 슬라이스 테스트 쓰면 **해당 모듈 test 스타터 추가 + import 패키지 `org.springframework.boot.<module>.test.autoconfigure`로 수정**. autoconfig 클래스 못 찾으면 캐시의 4.x jar에서 실제 패키지 경로 확인.
- 관련 테스트: `RegistrationControllerTest`(@SpringBootTest+@AutoConfigureMockMvc+Testcontainers).

### 2026-07-08 공유 컨테이너 @SpringBootTest — 커밋 누수로 전역 count 단정 깨짐
- 맥락: 슬라이스 2 추가 후 `RegistrationControllerTest.products.count()==1`이 실패. 슬라이스 2 테스트들이 product를 커밋해 남긴 탓.
- 원인: @SpringBootTest는 컨텍스트(=컨테이너 postgres)를 공유하고 기본은 롤백 없음(@BeforeEach deleteAll로만 격리). 전역 개수를 단정하는 테스트는 다른 테스트가 남긴 커밋 데이터에 오염된다.
- 규칙화된 교훈 (원인→해결): DB 통합 테스트는 **① 특정 variantId 등으로 스코프**해 자연 격리하거나, **② `@Transactional`(테스트 메서드 tx 롤백)로 커밋 누수 차단**. MockMvc도 동일 스레드라 @Transactional tx가 컨트롤러 리드까지 전파됨. 전역 count 단정은 피하거나 @Transactional 필수. (기존 `RawDealPostUpsertTest`는 @BeforeEach deleteAll 방식 유지 — 혼용 가능.)
- 관련 테스트: `RegisterProductUseCaseTest`·`RegistrationControllerTest`·`GetBenchmarkUseCaseTest`·`BenchmarkControllerTest`.

### 2026-07-08 ddl-auto=validate — smallint 컬럼을 Integer로 매핑하면 검증 실패
- 맥락: alert_policy 엔티티 추가 후 컨텍스트 로드 전부 실패(`SchemaManagementException`). 메시지: `column [quiet_hours_end] found [int2 (SMALLINT)], but expecting [integer (INTEGER)]`.
- 원인: 스키마 컬럼이 `smallint`(int2)인데 JPA 필드를 `Integer`로 매핑 → validate가 INTEGER를 기대해 불일치. (한 엔티티 매핑 오류가 EntityManagerFactory 생성을 막아 모든 @SpringBootTest가 컨텍스트 로드 실패로 무더기 FAIL — 22건.)
- 규칙화된 교훈 (원인→해결): DDL 타입과 JPA 필드 타입을 정확히 맞춘다. Java `Integer`를 유지하려면 **`@JdbcTypeCode(SqlTypes.SMALLINT)`**로 컬럼 JDBC 타입을 명시(또는 필드를 `Short`로). 컨텍스트 로드 대량 실패 = 스키마 검증 불일치 의심 → 리포트에서 `Schema-validation` 라인 확인.
- 관련 테스트: `EvaluateAlertOnDealUseCaseTest`(alert_policy) 외 전 @SpringBootTest.

### 2026-07-09 지수 백오프 — `min(delay, cap)`은 cap을 곱셈 **뒤에** 적용해 오버플로
- 맥락: collector 스케줄러 `backoff_delay(failures, policy)` 구현. 포화 테스트를 `failures=99`로 잡았다.
- 증상: `test_backoff_saturates_at_cap`이 `OverflowError: Python int too large to convert to C int`로 실패. 다른 26개는 통과.
- 원인: `policy.base * (policy.factor ** (failures - 1))` — `timedelta.__mul__`이 `2**98`을 C int로 변환하려다 터진다. `min(delay, cap)`은 delay가 **이미 만들어진 뒤** 실행되므로 보호막이 못 된다. 상한이 있는데도 상한 없는 중간값을 만든 게 실수.
- 규칙화된 교훈 (원인→해결): **포화(cap)가 있는 지수식은 cap 비교를 곱셈 전에 스칼라로 수행**한다 — `total_seconds()` float로 계산하고 `>= cap_seconds`면 즉시 cap 반환(float 지수는 넘치면 `inf`라 비교가 그대로 성립). 일반화: "상한이 있는 계산은 상한 없는 중간값을 만들지 않는다". 그리고 **포화 테스트의 입력은 경계 바로 위(예 5)가 아니라 극단값(99)으로** 잡아야 이런 오버플로가 드러난다.
- 관련 테스트: `collector/tests/test_scheduler.py::test_backoff_saturates_at_cap`.

### 2026-07-09 SEC-08 — "모든 실패에 백오프"는 크롤링 윤리 위반, 차단 신호는 별도 경로
- 맥락: 스케줄러 재시도 정책 설계. `docs/11`은 "지수 백오프", SEC-08(`docs/20`)은 "차단 신호(403/429) 감지 시 자동 중지 + 관리 알림 — **재시도 강행 금지**".
- 증상(설계 단계에서 포착): 관용적 구현(`except: backoff; retry`)을 그대로 쓰면 403/429에도 재시도가 붙어 SEC-08을 정면 위반한다. 두 문서를 각각 읽으면 충돌이 안 보인다.
- 원인: "실패"를 단일 개념으로 뭉갠 것. 일시 장애(5xx·타임아웃)와 플랫폼의 거절(403/429)은 **의미가 반대**다 — 전자는 "잠시 후 다시", 후자는 "오지 마라".
- 규칙화된 교훈 (원인→해결): 폴링 결과는 **`OK / TRANSIENT / BLOCKED` 3분해**하고 BLOCKED만 중지 경로(사이트별 `stopped` + Alert)로 보낸다. 레이트 하한은 설정이 아니라 **코드 상수**로 못박는다(SEC-08 "완화 불가"). 일반 규칙: **외부 시스템의 거절 신호를 재시도 가능한 오류로 분류하지 않는다.**
- 관련 테스트: `test_classify_status`, `test_blocked_stops_the_site_without_retry`, `test_stopped_site_is_never_fetched_again`, `test_configured_interval_below_floor_is_clamped_in_cycle`.

### 2026-07-09 차단 장치는 "무엇을 통과시키는가"를 먼저 테스트해야 한다 (오차단이 진짜 위험)
- 맥락: `PreToolUse` 가드레일(`.claude/hooks/guard.sh`)로 `git push`·실사이트 호출을 차단. 계약 테스트 18건(차단 8 + 통과 10)이 GREEN이라 배포했다.
- 증상: 사후 검증에서 **오차단 4건** 발견 — `grep -rn "git push" docs/`, `echo "git push"`, `grep -rn "curl https://…ppomppu…" docs/`, 그리고 **무해한 `ls`가 description 때문에 차단**. 전부 실제 개발 중 칠 법한 명령이다.
- 원인: (1) JSON의 `"command":"` 를 앵커로 잡으려 구분자 문자군에 `"`를 넣어, 사용자 따옴표 뒤도 "명령 시작"으로 오인. (2) **stdin 전체를 grep** — Bash 도구의 `description` 필드까지 매칭됐다. (3) 통과 테스트 10건을 내가 골랐는데, 하필 이 네 형태를 안 골랐다.
- 규칙화된 교훈 (원인→해결): **차단 장치(훅·가드·필터)는 차단 케이스보다 통과 케이스를 먼저·많이 짠다.** 오차단은 조용히 작업을 마비시키지만 미차단은 다른 방어선이 받는다. 검사 대상은 **입력 전체가 아니라 해당 필드만 파싱**한다(주변 필드 오염 차단). 앵커 문자군에 데이터 문자(`"`)를 섞지 않는다. 그리고 매 호출마다 도는 훅은 **서브프로세스를 쓰지 않는다** — `grep`/`sed` 3개가 229ms였고, bash 내장 `[[ =~ ]]`로 85ms가 됐다.
- 관련 테스트: `.claude/hooks/guard.test.sh`(오차단 4건을 회귀로 고정).

### 2026-07-09 설정 스키마는 요약이 아니라 원문·JSON 스키마로 확인한다
- 맥락: `.claude/settings.json`에 훅·permissions를 작성. 문서 요약 모델에게 스키마를 물었다.
- 증상: 요약이 훅 구조를 `{"on-session-start": {"type":"http"}}`로, permissions 문법을 `Bash(npm run test *)`만 있는 것처럼 알려줬다. 실제는 `{"SessionStart":[{"hooks":[{"type":"command"}]}]}`. 그리고 나는 반대로 "공백형은 틀렸고 `:*` 콜론형이 맞다"고 단정했는데, 원문은 **둘이 동등**이라고 명시한다. `cd X && cmd`가 allow 규칙에 안 걸린다고도 단정했으나, 원문은 **하위명령별로 독립 매칭**한다고 한다.
- 원인: 요약 모델(및 서브에이전트)의 사실 주장을 원문 대조 없이 채택. 내장 Explore·Plan 에이전트는 CLAUDE.md조차 로드하지 않는다(공식 문서).
- 규칙화된 교훈 (원인→해결): **로드베어링한 사실(경로·타입·필드명·설정 키·문법)은 원문 또는 JSON 스키마로 확인한 것만 쓴다.** Claude Code 설정은 `json.schemastore.org/claude-code-settings.json`이 최종 권위다. 두 출처가 엇갈리면 둘 다 의심한다. **틀린 단정을 했으면 즉시 정정한다** — 잘못된 확신은 침묵보다 해롭다.
- 관련 테스트: 없음(문서·설정). `.claude/settings.json`이 스키마 검증을 통과하는지로 확인.

### 2026-07-09 우리가 적어둔 "실측" 결론도 틀릴 수 있다 — 뽐뿌 fixture는 처음부터 멀쩡했다
- 맥락: `docs/98`·`docs/91` Q-5에 "뽐뿌 응답에 `revolution_main_table`·`baseList` 계열 셀렉터가 **전무**, golden 부적합, 재채취 필요"라고 기록돼 있었다. 그 탓에 핫딜 3사 중 뽐뿌만 파서 없이 남았고 기준가 표본이 2사뿐이었다.
- 증상: 다음 작업을 고르려 fixture를 직접 열어보니 딜 행 **21건**이 정상 파싱됐다. 제목·글번호·추천·URL 전부 존재. 없는 건 `id="revolution_main_table"` **요소** 하나뿐(JS 문자열 안에만 등장)이었다.
- 원인: 오픈소스 셀렉터 체인이 그 테이블 id에서 시작하는데 첫 단계가 끊기자, "행 셀렉터도 없다"로 **일반화**했다. 실제로 행을 세보지 않았다. 부수적으로, raw `grep 'class="baseList-title"'`는 8건인데 bs4로 세면 27건이다(속성 표기·따옴표 차이) — **문자열 카운트가 파서 결과를 대변하지 못한다.**
- 규칙화된 교훈 (원인→해결): **"셀렉터가 없다"는 주장은 파서로 재현해 확인한다.** 상위 셀렉터 실패를 하위 셀렉터 부재로 일반화하지 않는다. 검증엔 `grep` 카운트가 아니라 실제 파서(bs4)를 쓴다. 그리고 **`docs/98`·`docs/99`에 적힌 것도 가설로 취급**한다 — 기록은 그때의 관측이지 영구 진실이 아니다. 문서가 작업을 막고 있다면, 막기 전에 그 근거를 한 번 재현해본다.
- 추가 실측 함정: 뽐뿌는 `charset=euc-kr`을 **선언**하지만 EUC-KR에 없는 바이트를 담아 `decode("euc-kr")`가 터진다. **`cp949`(확장)로 디코딩해야 한다.** `errors="replace"`로 덮으면 제목이 조용히 깨지므로 금지.
- 관련 테스트: `collector/tests/test_parsers.py::test_ppomppu_golden_rows` 외 3건.

### 2026-07-09 휴리스틱은 "1차 검증"을 기다리지 말고 지금 있는 golden으로 측정하라
- 맥락: `docs/91` Q-18("가격 파싱 오검출 여지, **1차 검증 후 정교화**")은 라이브 데이터를 기다리는 항목이었다. 뽐뿌 파서가 붙으며 fixture에 실 제목 49건이 모였다.
- 증상: 측정하니 오검출이 즉시 재현됐다 — `1,000mg*60정 (9,600원/무료)` → **1000**, `5600MHz 513,000` → **5600**, `콘드로이친 1200 …(34,710원/무료)` → **1200**. 배송비 관례 `(13,490원/3,000원)`도 합산되지 않아 BM-02 AC-1을 위반하고 있었다.
- 원인: "4자리 이상 숫자를 가격 후보로 보고 **첫 매치**를 취한다"는 규칙. 한국 핫딜 제목은 함량(`1,000mg`)·규격(`32GB`)·주파수(`5600MHz`)·수량(`100매`)이 가격보다 **앞에** 오는 일이 흔하다. 위치는 가격다움의 근거가 못 된다.
- 규칙화된 교훈 (원인→해결): **휴리스틱의 정체는 실 데이터로만 드러난다. 라이브를 기다리지 말고 이미 가진 golden으로 전수 측정하라** — fixture는 파서 검증용만이 아니라 **하류 규칙의 시험대**다. 그리고 오검출은 정규식을 덧대서가 아니라 **후보 서열**(만원 > `원` 동반 > 콤마 > 맨 숫자)로 푼다. 위치가 아니라 신뢰도로 고른다. 개선 시엔 **before/after 전수 대조**로 "의도한 N건만 바뀌었음"을 증명한다(69건 중 7건 변경, 62건 불변).
- 파급: 오검출 `1000`·`1200`·`5600`이 기준가 표본에 들어가면 Tukey 하한을 뚫어 LOWER 이상치로 분류되고, BM-05상 **🔥 최우선 오알림**이 된다. 파싱 결함 하나가 알림 신뢰를 무너뜨린다.
- 관련 테스트: `collector/tests/test_price.py::test_unit_bearing_numbers_are_not_prices` 외 실 제목 회귀 7건.

### 2026-07-09 부품별 GREEN은 계약을 보장하지 않는다 — `ENDED` vs `SOLD_OUT`이 3일 잠복했다
- 맥락: collector의 파서(4사)·가격 정규화·스케줄러·적재 레코드 변환이 전부 각자 GREEN이었다. 그런데 **함께 돌려본 테스트가 하나도 없었다.**
- 증상: 종단 스모크를 짜려고 `parse_bunjang` → `to_raw_records`를 처음 연결하자마자 `ValueError: 알 수 없는 status: 'ENDED'`. 파서가 내는 어휘(`ENDED`)가 소비처의 허용집합(`ACTIVE/SOLD_OUT/DELETED`)에도 DB CHECK에도 없었다. `ENDED`는 `deal_event.status`의 값이었다.
- 원인: 파서 테스트는 파서만, ingest 테스트는 **손으로 만든** `ParsedDeal`만 봤다. 양쪽 다 자기 세계 안에서 옳았다. 두 부품의 **경계에 있는 어휘**는 어느 테스트도 지키지 않았다.
- 규칙화된 교훈 (원인→해결): **부품 사이에 값이 흐르면 그 경로를 관통하는 테스트를 하나 둔다.** 이건 "통합 테스트"가 아니라 **계약 검증**이다 — 느리고 광범위한 E2E가 아니라, `A의 출력을 B에 그대로 먹이는` 좁은 테스트면 충분하다(`to_raw_records(parse_bunjang(payload))`). 그리고 소비처의 허용집합은 **손으로 만든 값이 아니라 생산자가 실제로 낼 수 있는 값 전부**로 시험한다. 도메인 어휘가 계층마다 다르면(raw 상태 vs 이벤트 상태) 이름을 재사용하지 말 것.
- 관련 테스트: `test_ingest.py::test_accepts_every_status_the_bunjang_parser_can_emit`, `test_pipeline_smoke.py`(fixture 바이트 → RawDealRecord 관통).

### 2026-07-09 `capsys`는 콘솔 인코딩을 대신 검증하지 못한다 — em dash가 엔트리포인트를 죽였다
- 맥락: `collector/__main__.py`에 opt-in 안내 문구를 넣고 `capsys`로 출력을 단언했다. 4개 테스트 전부 GREEN.
- 증상: 계획의 검증 항목대로 **실제로 `python -m collector`를 돌리자** `UnicodeEncodeError: 'cp949' codec can't encode character '—'`로 exit 1. Windows 콘솔 기본 인코딩이 cp949인데 문구에 `—`(em dash)와 `⚠️`를 썼다. 한글은 cp949에 있어서 멀쩡했고, 저 두 글자만 없었다.
- 원인: `capsys`(및 pytest의 캡처)는 **utf-8 텍스트 스트림으로 캡처**한다. 실제 콘솔의 인코더를 타지 않으므로 인코딩 불가 문자를 통과시킨다. "출력 문자열이 맞는가"만 봤지 "출력이 가능한가"는 안 봤다.
- 규칙화된 교훈 (원인→해결): **엔트리포인트는 테스트가 GREEN이어도 한 번은 실제로 실행해본다.** 그리고 stdout에 나갈 문자열은 `text.encode("cp949")`로 **인코딩 가능성을 직접 단언**한다(로그·알림 문구 포함 — Alert reason도 결국 출력된다). 콘솔 출력엔 em dash·이모지·타이포그래피 문자를 쓰지 않는다. 문서엔 써도 되지만 `print`엔 안 된다.
- 관련 테스트: `test_main.py::test_refusal_message_is_console_encodable`, `::test_alert_and_summary_output_are_console_encodable`.

### 2026-07-09 문서와 실행 가능한 계약이 모순이면 실행되는 쪽이 진실이다 + 테스트 스키마는 미러 말고 원본을 써라
- 맥락: collector의 DB 적재기(Q-36)를 짜려고 `raw_deal_post` 쓰기 규약을 확인했다.
- 증상: `docs/01`과 `collector/README`는 "collector가 **insert-only**"라고 세 곳에서 말한다. 그런데 core의 `RawDealPostUpserter`는 `refreshFrom(url, title, capturedAt, status)`로 **갱신**하고, 그 테스트는 "상태 변화는 기존 행에 반영"(BM-01 AC-2)을 단언한다. insert-only면 품절을 영원히 모른다. 곁가지로 그 업서터는 **프로덕션에서 아무도 호출하지 않는** 명세 전용 코드였다.
- 원인: 산문은 검증되지 않는다. 문서는 초기 설계 의도("collector는 덮어쓰지 않는다")를 적었고, 이후 AC-2가 갱신을 요구하며 코드가 앞서갔는데 문서만 남았다.
- 규칙화된 교훈 (원인→해결): **모순을 발견하면 실행되는 쪽(테스트·DDL·CHECK 제약)을 믿고 문서를 고친다.** 산문은 GREEN일 수 없다. 그리고 **테스트용 스키마 미러를 만들지 마라** — 미러는 반드시 드리프트하고, 드리프트한 미러는 **GREEN인 채로 거짓말한다**. collector의 통합 테스트는 core의 `V1__init.sql`을 **그대로 읽어 적용**한다. core가 계약을 바꾸면 collector가 즉시 깨진다. 모듈 경계를 넘는 계약은 한쪽이 소유하고 다른 쪽은 **원본을 참조**한다.
- 부수: `ON CONFLICT DO UPDATE`에서 "불변이어야 하지만 나중에 알 수도 있는" 컬럼은 `COALESCE(기존, 신규)`로 쓴다(`posted_at` — 발생 시각 불변 + 후채움).
- 관련 테스트: `collector/tests/test_raw_deal_sink.py`(10건, core V1 스키마 적용), `conftest.py`.

### 2026-07-09 파서는 구조 변경에 터지지 않는다 — 조용히 0건을 낸다 (REL-06)
- 맥락: REL-06 드리프트 감지(Q-40)를 짜며 "무엇을 감지해야 하나"를 정의해야 했다.
- 증상(과거 사례): 뽐뿌 셀렉터 체인(`table#revolution_main_table` → 행)이 첫 단계에서 끊겼을 때, 파서는 **예외 없이 빈 목록**을 반환했다. 실패가 아니라 성공으로 보였고, 그 결과가 문서에 "셀렉터 전무"로 잘못 기록된 채 석 달을 갔다.
- 원인: `soup.select(...)`는 못 찾으면 `[]`를 준다. 셀렉터 기반 파싱의 실패 모드는 **예외가 아니라 침묵**이다. try/except는 이걸 못 잡는다.
- 규칙화된 교훈 (원인→해결): **"성공했는데 산출물이 0"을 1급 경보 신호로 다뤄라.** 예외율만 보면 구조 변경을 영원히 놓친다. 드리프트 감지는 ① 조용한 0건 연속 ② 성공률 저하를 함께 본다. 차단(BLOCKED)은 세지 않는다 — 이미 중지+알림으로 처리되므로 겹치면 소음이다. 그리고 **창을 채우기 전엔 판정하지 않고**(기동 직후 오탐), **같은 증상은 한 번만 알리고 회복 시 재무장**한다(알림 피로).
- 관련 테스트: `collector/tests/test_drift.py`(12건), `test_main.py::test_silent_zero_yield_raises_a_console_safe_drift_alert`.

### 2026-07-09 "미래면 어제"는 시계 오차를 24시간 오차로 증폭한다
- 맥락: 게시판 목록의 `21:10:11`(시각만)을 절대 시각으로 바꾸려면 "오늘"이 필요하다. 자정 직후 폴링을 대비해 "계산된 시각이 미래면 어제 글"로 짰다.
- 증상: 테스트가 잡았다. 폴링 시각이 21:00 KST일 때 `21:10:11` 글이 10분 미래가 되고, 규칙대로면 **어제 21:10**이 된다. 우리 시계가 11분 뒤처진 것뿐인데 `firstSeen`이 24시간 틀어진다.
- 원인: "미래"라는 신호를 **원인 구분 없이** 하나의 보정으로 처리했다. 시각만 있는 값이 *멀리* 미래일 수 있는 경우는 자정 경계뿐이고, *조금* 미래인 것은 시계 오차다.
- 규칙화된 교훈 (원인→해결): **보정 규칙은 "얼마나 어긋났는가"로 원인을 갈라라.** 여기서는 12시간 임계 — 그 이상이면 날짜 경계, 이하면 시계 오차라 그대로 둔다. 일반화: **오차를 고치려는 보정이 원래 오차보다 큰 오차를 만들면 안 된다.** 되돌릴 수 없는 필드(`firstSeen` — 기간 필터·CAD·성적표가 전부 의존)일수록 보정에 보수적이어야 한다.
- 관련 테스트: `test_timestamps.py::test_far_future_time_is_read_as_yesterday`, `::test_slightly_future_time_stays_today`.

### 2026-07-09 nginx `proxy_pass`는 기동 시점에 업스트림을 해석한다 — 프록시 대상이 죽으면 프록시도 안 뜬다
- 맥락: `web` 컨테이너의 nginx가 `/api`를 `core:8080`으로 프록시해 CORS를 원천 제거하려 했다. `proxy_pass http://core:8080;`으로 적고 이미지를 빌드했다.
- 증상: 컨테이너가 뜨자마자 죽었다. `nginx: [emerg] host not found in upstream "core"`. **정적 페이지조차 서빙되지 않았다.** compose에선 `core`가 먼저 뜨니 가려졌겠지만, core가 재시작하거나 늦게 뜨면 web 전체가 무너진다.
- 원인: `proxy_pass`에 **리터럴 호스트**를 쓰면 nginx가 설정 로드 시점에 DNS를 해석한다. 실패하면 기동 자체가 실패한다. Docker 내장 DNS는 컨테이너가 뜬 뒤에야 대상을 알 수 있다.
- 규칙화된 교훈 (원인→해결): **`proxy_pass`의 업스트림을 변수로 두고 `resolver`를 지정한다** (`resolver 127.0.0.11; set $up "core:8080"; proxy_pass http://$up;`). 그러면 해석이 **요청 시점**으로 미뤄져, 대상이 없으면 502를 돌려줄 뿐 프록시는 살아 있다. 일반화: **A가 B를 프록시할 때 A의 가용성이 B에 종속되면 안 된다.** 그리고 `depends_on`은 "떴다"만 보장하지 "준비됐다"를 보장하지 않으므로, 종속을 없앨 수 있으면 없앤다.
- 부수: 이미지는 **실제로 띄워서** 확인해야 한다. `docker build` 성공은 기동 성공이 아니다.
- 관련 테스트: 없음(인프라). `docker run` 후 `GET /` 200 / `GET /api/...` 502로 확인.

### 2026-07-09 모듈 테스트가 전부 GREEN이어도 스택은 안 뜬다 — 종단 스모크를 자동화하라
- 맥락: core 260 / collector 158 / web 26 테스트가 전부 GREEN이었다. 그런데 **전체 스택을 한 번도 함께 띄워본 적이 없었다.** `docker compose up`을 처음 실행했다.
- 증상 3연발: ① 호스트에 로컬 Postgres가 5432를 잡고 있어 **포트 하나 때문에 스택 전체가 기동 실패**했다. ② nginx가 `proxy_pass`의 리터럴 업스트림을 기동 시점에 해석하다 죽었다(직전 교훈). ③ 등록 POST가 400 — `Invalid UTF-8 start byte 0xbd`. Windows `curl.exe`가 **argv의 한글을 cp949로** 넘겼다.
- 원인: 단위·통합 테스트는 프로세스 안에서 돈다. 컨테이너 경계·포트 바인딩·프록시 DNS·argv 인코딩은 **프로세스 밖의 계약**이라 아무 테스트도 보지 않는다.
- 규칙화된 교훈 (원인→해결): **브라우저가 걷는 길을 그대로 걷는 스모크를 스크립트로 만들고 CI에 건다**(`scripts/smoke.sh`: 빌드 → 기동 → 정적 자산 → SPA 폴백 → 프록시 → 등록 POST → DB 왕복 → 목록 반영). 부수 규칙: **호스트 포트는 전부 환경변수로 비켜갈 수 있게** 한다(포트 하나가 스택을 막으면 안 된다). **비ASCII를 curl argv에 싣지 말고 `-d @file`로 보낸다**(Windows argv는 cp949로 변환된다 — 뽐뿌 cp949 사고와 같은 계열).
- 격리 규율: 스모크는 **전용 compose 프로젝트 이름**으로 띄운다. 마지막 `down -v`가 개발용 `pgdata` 볼륨을 지우면 안 된다.
- 관련 테스트: `scripts/smoke.sh` (CI `smoke` 잡, `needs: [core, collector, web]`).

### 2026-07-09 기록 보드도 코드처럼 무결성 검사가 필요하다 — Q-41이 "해소"와 "열림"에 동시에 있었다
- 맥락: `docs/91`에서 항목을 해소할 때 상단에 stub(`_(Q-N … 해소됨 …)_`)을 넣고 원본 섹션을 지운다. Q-41은 stub만 넣고 **원본 `## [열림]` 섹션을 지우지 않았다**.
- 증상: 세션 브리핑이 Q-41을 열린 항목으로 계속 출력했다. 다음 세션이 **이미 고친 결함(`ENDED` → `SOLD_OUT`)을 다시 고치려 들었을** 것이다. 보드는 "말로 끝내지 않기" 위한 장치인데, 스스로 거짓을 말하고 있었다.
- 원인: 문서 편집엔 컴파일러가 없다. stub 추가와 원본 삭제는 **두 개의 편집**이고 둘째를 빼먹어도 아무도 모른다.
- 규칙화된 교훈 (원인→해결): **기록 보드에도 무결성 검사를 건다.** 같은 식별자가 "해소"와 "열림"에 동시에 나타나면 `SessionStart` 훅이 경고한다(`.claude/hooks/session-brief.sh`). 일반화: **사람이 손으로 유지하는 인덱스·상태 표기는 반드시 어긋난다** — 어긋남을 자동으로 드러내는 값싼 검사를 붙여라. 그 검사도 **일부러 모순을 주입해** 실제로 잡히는지 확인한다.
- 부수: 같은 훅의 드리프트 지표(교훈 19 vs 축적된 규칙 2)가 승격 누락을 가리키고 있었다. 지표를 만들었으면 **그 지표를 읽어야** 한다.
- 관련 테스트: 없음(훅). 임시 저장소에 `## [열림] Q-38`을 되살려 경고가 뜨는지 확인.

### 2026-07-09 보안 장치는 "막힌다"만 보지 말고 "통과된다"까지 봐야 한다 — 401은 나는데 200이 500이었다
- 맥락: web에 nginx Basic Auth(SEC-02)를 붙였다. 자격증명 없이 요청 → **401**. 여기까지만 봤으면 통과였다.
- 증상: 올바른 자격증명으로 요청하니 **500**. `nginx: [crit] open("/etc/nginx/.htpasswd") failed (13: Permission denied)`. 엔트리포인트가 `chmod 600` + root 소유로 만들었는데 워커는 `nginx`(uid 101)로 돈다.
- 원인: **인증 실패 경로와 성공 경로가 다른 코드를 탄다.** 401은 파일을 읽기 전에 결정되고(자격증명 없음), 파일은 검증할 때 비로소 열린다. "막힌다"만 확인하면 잠긴 문이 아니라 **부서진 문**을 통과시킨 셈이다.
- 규칙화된 교훈 (원인→해결): **차단 장치는 차단만이 아니라 허용도 테스트한다.** 이미 `guard.sh`에서 배운 "오차단이 진짜 위험" 규칙의 거울상이다 — 인증에서는 **오통과가 아니라 오차단이 조용히 숨는다**(운영자만 겪는다). 그리고 컨테이너에서 시크릿 파일은 `600 root:root`가 아니라 **`640 root:<워커그룹>`** 이다. 세상에 열지 않되 워커에겐 읽혀야 한다.
- 관련 테스트: `scripts/smoke.sh` 7단계 — 인증 끈 경로(1~6)와 켠 경로(401 + 200)를 모두 지난다.
