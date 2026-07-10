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

### 2026-07-09 백업 스크립트가 exit 0을 내는 것과 복원되는 것은 다른 사건이다
- 맥락: REL-04로 `scripts/backup.sh`(pg_dump + gzip)를 썼다. 돌렸더니 8KB 파일이 생기고 exit 0. 여기서 멈추면 "백업 있음"으로 체크리스트에 적었을 것이다.
- 증상: 실제로 일회용 컨테이너에 부어보기 전까지, 그 8KB가 **복원 가능한지 아무도 몰랐다.** 확인해야 했던 건 파일 존재가 아니라 (a) gzip 무결성 (b) 테이블·행의 실제 복귀 (c) `flyway_schema_history`의 복귀였다. (c)가 빠지면 core가 재기동하며 **이미 있는 스키마에 마이그레이션을 다시 시도한다** — 복원이 성공한 것처럼 보이다가 부팅에서 죽는다.
- 원인: 백업은 **쓰기 경로만** 실행한다. 읽기 경로(복원)는 사고가 나야 처음 실행되는 코드다. 테스트되지 않은 코드는 동작하지 않는다는 규칙에 예외가 없다.
- 규칙화된 교훈 (원인→해결): **복구 절차는 리허설로만 존재를 증명한다.** 백업 스크립트와 **짝이 되는 복원 드릴**을 같은 커밋에 넣고, 드릴은 **일회용 격리 컨테이너**(tmpfs·볼륨 없음·다른 프로젝트 이름)에서만 돈다 — 복원을 검증하려다 운영 DB를 덮는 것이 이 종류의 스크립트가 내는 최악의 사고다. 드릴은 "복원됐다"가 아니라 **다음 기동이 성공할 조건**(스키마 이력)을 단언한다. 같은 논리가 `db/rollback/`에도 적용된다 — 롤백 스크립트는 있는데 그걸 실행하는 테스트가 없다(Q-51).
- 관련 테스트: `scripts/restore-drill.sh` (실측 PASS: 제품 1행이 덤프를 거쳐 새 컨테이너에서 되살아남, 테이블 13개, flyway 이력 존재).

### 2026-07-09 콘솔 인코딩 문제는 우회하는 게 아니라 출력 계약을 바꿔 없앤다
- 맥락: 어제 em dash 한 글자가 cp949 콘솔에서 collector 엔트리포인트를 죽였다. 그때의 대응은 "출력 문구에서 em dash·이모지를 쓰지 말 것"이라는 **금지 규칙**이었다. 금지는 사람이 지켜야 하고, 사람은 잊는다.
- 증상: 규칙을 지켜도 남는 문제가 더 있었다. 스모크가 `grep "실 네트워크 폴링이 꺼져"` 같은 **산문을 문자열로 검사**하고 있었다. 문구를 다듬는 순간 스모크가 깨지고, 문구를 유지하려고 로그가 굳는다. 실 폴링을 켜면 사람은 `docker logs`만 보는데, 그 로그에서 "몇 건 실패했나"를 세려면 문장을 파싱해야 했다.
- 원인: stdout을 **사람이 읽는 산문**으로 정의한 것이 근본 원인이다. 그러면 인코딩·문구·파싱이 전부 결합된다.
- 규칙화된 교훈 (원인→해결): **관측 출력은 문장이 아니라 이벤트(JSON Lines)로 낸다**(OBS-01). `ensure_ascii=True`면 한글은 `\uXXXX`로 나가 **값은 온전한 채 바이트는 순수 ASCII**가 된다 — 인코딩 문제가 규칙이 아니라 구조로 사라진다. 테스트·스모크는 문구를 grep하지 말고 `json.loads`로 이벤트를 읽는다: 문구는 바뀌어도 계약은 안 바뀐다. 그리고 카운터에서 **0을 지우지 않는다** — `by_site`의 `{"ppomppu": 0}`은 "성공했는데 0건"이라는 드리프트의 전형이고, 필드를 생략하면 그 사실이 사라진다(실패·차단도 같은 이유로 따로 센다: 대응이 다르다).
- 관련 테스트: `collector/tests/test_observability.py`, `test_main.py`(`_assert_console_safe`가 `encode("ascii")`까지 단언), `scripts/smoke.sh` 6단계(`"event":"refused"`).

### 2026-07-09 화면을 그려보기 전까지 "정상 응답"인 거짓말은 드러나지 않는다 — currentPrice=0
- 맥락: core의 기준가 REST는 계약대로 동작했고 테스트도 전부 GREEN이었다. 네이버 키가 없어 `StubCurrentPriceProvider`가 현재가로 **0**을 반환하는 것도 주석에 정직하게 적혀 있었다.
- 증상: 그 0이 `gap = currentPrice − 기준가`를 타고 흐르면 `{won: -820000, pct: -100.0}`이 된다. **타입·스키마·상태코드 어디에도 이상이 없다.** 화면이 이걸 그리면 "지금 100% 싸다"는, 이 시스템이 낼 수 있는 **가장 강한 매수 신호를 거짓으로** 낸다. 더 조용한 쪽은 알림이다 — `AlertEvaluator`의 콜드스타트 잭팟은 `price ≤ currentPrice·(1−ratio)`라서 임계가 0이 되고 **어떤 딜도 통과하지 못한다**(놓침). 하필 그 경로는 기준가가 없는 초기, 즉 지금을 위해 만든 것이다.
- 원인: **"미확립"을 도메인 타입이 아니라 sentinel(0)로 표현**했다. `null`이면 컴파일러와 계산이 막아주지만 `0`은 유효한 `long`이라 어디든 흘러간다. SPARSE/NONE의 통계 필드는 `null`로 못박아 둔 프로젝트가, 현재가만 sentinel로 남겨뒀다.
- 규칙화된 교훈 (원인→해결): **"값 없음"을 값으로 표현하지 않는다.** 스텁이 반환하는 sentinel은 스텁 안에 갇히지 않고 산식·알림·화면까지 흐른다. 불가피하면 **해석을 한 곳에 가둔다**(`CURRENT_PRICE_UNAVAILABLE` 상수 + `gapLine` 하나, refactor seam) — 소비자가 늘어나기 전에. 그리고 일반화: **표시 계층을 만들면 도메인의 거짓말이 드러난다.** "화면은 나중에"는 검증을 나중으로 미루는 것과 같다.
- 관련 테스트: `web/src/decision/present.test.ts`(`currentPrice=0`이면 갭 대신 미확립), `scripts/smoke.sh` 5-1단계(딜 0건 variant → `tier:NONE`·`GRAY`·`guardMet:false`, 없는 variant → 404). 보드: `docs/91` Q-53.

### 2026-07-09 우회 스위치는 "필요한 명령에만" 건다 — MSYS_NO_PATHCONV가 스모크를 죽였다
- 맥락: `restore-drill.sh`에서 Git Bash가 도커 볼륨 인자 `/repo`를 `C:/Program Files/Git/repo`로 바꾸는 걸 막으려 `MSYS_NO_PATHCONV=1`을 배웠다. 그 뒤 습관적으로 `MSYS_NO_PATHCONV=1 bash scripts/smoke.sh`로 실행했다.
- 증상: `curl: Failed to open /tmp/tmp.nVFYWtUsWm` → 등록 POST 실패. 스모크가 5단계에 닿지도 못했다. 경로 변환을 끄자 `mktemp`가 준 **MSYS 경로**를 Windows `curl.exe`가 열 수 없게 된 것이다. 정작 그 `-d @file`은 **다른 우회**(argv cp949 회피)로 도입한 것이었다.
- 원인: 경로 변환 억제는 **프로세스 전체**에 걸리는데, 그 프로세스 안엔 변환이 필요한 명령과 방해받는 명령이 섞여 있었다. 우회끼리 충돌했다.
- 규칙화된 교훈 (원인→해결): **환경변수 우회는 스크립트 바깥에서 걸지 말고, 그게 필요한 명령 앞에만 붙인다**(`MSYS_NO_PATHCONV=1 docker run …`). 스크립트는 자기가 필요한 우회를 스스로 지녀야 호출자가 실수할 수 없다. 부수: 스모크가 **크게 실패**해줘서 30초 만에 원인을 짚었다 — 조용히 건너뛰었다면 새 단계가 안 돈 줄도 몰랐을 것이다.
- 관련 테스트: `bash scripts/smoke.sh`(우회 없이 그대로 실행되어야 한다).

### 2026-07-09 헬스체크는 "정의했다"가 아니라 "healthy를 보고한다"까지 확인한다 (그리고 인증 뒤에 숨으면 안 된다)
- 맥락: OBS-04로 compose healthcheck를 core·web에 붙였다. YAML을 쓰는 건 5분이다.
- 증상(예방된 것): 헬스체크 명령은 **이미지 안에서** 돈다. `curl`이 없는 JRE 이미지, `wget`이 없는 슬림 이미지에선 명령이 실패해 컨테이너가 **영원히 unhealthy**로 굳는다 — 그런데 서비스 자체는 멀쩡히 응답한다. 더 고약한 건 web이다: `auth_basic`을 server 레벨에 걸었으므로 `/`를 찌르는 헬스체크는 **401을 받고 unhealthy**가 된다. 즉 접근 통제를 켜는 순간 헬스체크가 죽는다.
- 원인: 헬스체크는 컨테이너 **안**에서, 인증 **뒤**에서 실행된다. 밖에서 `curl localhost:3000`이 되는 것과 아무 상관이 없다.
- 규칙화된 교훈 (원인→해결): ① 헬스체크를 붙였으면 **`compose ps`가 `healthy`를 말하는지 스모크로 확인**한다(정의 ≠ 동작). ② 이미지에 그 명령이 있는지 `docker run --rm <image> command -v curl wget`으로 **실측**한다. ③ 헬스 경로는 `auth_basic off`인 전용 엔드포인트(`/healthz`)로 두고, **인증을 켠 상태에서도 200인지** 테스트한다(SEC-02 Basic Auth 사고의 거울상 — 그때는 성공 경로가 500이었다). ④ **배치 컨테이너엔 헬스체크를 걸지 않는다** — 1회 실행 후 종료하는 collector는 살아 있음을 물으면 항상 죽어 있다.
- 관련 테스트: `scripts/smoke.sh` 0단계(`core:healthy`·`web:healthy` 보고 대기 + `/healthz`=ok), 7단계(auth 켠 상태에서 `/healthz`=200).

### 2026-07-09 상주 프로세스에는 "종료 계약"이 있다 — 그리고 그 계약을 compose가 읽는다
- 맥락: collector의 `main`은 opt-in을 켜면 `while True`로 도는 상주 루프다. 그런데 나는 바로 전 커밋의 compose 주석에 **"1회 실행 후 종료하는 배치"**라고 적었다. opt-in이 꺼진 상태만 보고 일반화한 것이다(그 상태에선 정말 exit 0으로 끝난다).
- 증상 3가지: ① `SIGTERM` 핸들러가 없어 `docker stop`이 사이클 한복판에서 프로세스를 찢는다. ② 틱 대기가 `time.sleep(15)`인데 **PEP 475 이후 sleep은 신호 처리 뒤 남은 시간을 마저 잔다** — 15초를 마저 자는 사이 docker 기본 유예(10초)가 끝나 SIGKILL이 온다. ③ `sink.upsert_all`이 `run_cycle`의 예외 격리 **바깥**에 있어, DB 재시작 한 번이 수집 루프 전체를 죽인다(사이트별 격리 REL-02는 있는데 적재 격리는 없었다).
- 원인: 프로세스의 **수명**을 아무도 계약으로 적지 않았다. 그래서 재시작 정책(`restart:`)도 없었고, 있었다면 `always`를 골라 opt-in 꺼진 컨테이너가 refused 메시지를 영원히 반복했을 것이다.
- 규칙화된 교훈 (원인→해결): **상주 프로세스는 종료 코드로 말한다.** 정상 종료(0)와 포기(1)를 구분해 적고, 재시작 정책이 그 구분에 기대게 한다(`on-failure` — `always`는 정상 종료까지 되살린다). 신호는 **현재 작업 단위를 마치고** 나가고, 틱 대기는 `time.sleep`이 아니라 `Event.wait`로 두어 즉시 깨어난다. 적재 실패는 뭉개지도 죽지도 않는다 — 유실 건수를 이벤트로 남기고, **연속 실패가 임계를 넘으면 스스로 내려온다**(수집은 되는데 저장이 안 되는 상태가 가장 나쁘다).
- 부수(실측): PID 1이 `uv`인데 `uv run python`도 SIGTERM을 자식에게 전달한다 — 컨테이너 두 개(`uv run` vs `.venv/bin/python`)를 띄워 `docker stop` 시간·종료코드·로그로 확인했다. **추측으로 Dockerfile을 고치지 않았다.**
- 관련 테스트: `tests/test_main.py`(종료 신호가 사이클을 마치고 나간다 / 틱 대기를 기다리지 않는다 / sink 실패가 루프를 죽이지 않는다 / 연속 실패는 exit 1 / 성공이 카운터를 리셋한다).

### 2026-07-09 "미러를 안 만든다"고 해놓고 V1만 적용하고 있었다 — 부분 적용도 미러다
- 맥락: collector 통합 테스트는 계약 미러를 만들지 않고 core의 실 마이그레이션을 적용한다. 그런데 `conftest.py`가 읽는 파일은 **`V1__init.sql` 하나**였다. 운영 DB는 V1+V2다.
- 증상(잠복): 지금은 `raw_deal_post`가 V1에만 있어 우연히 무해하다. 그러나 core가 V3에서 `raw_deal_post`에 컬럼·CHECK를 더하면 **collector 테스트는 V1 스키마 위에서 계속 GREEN**을 낸다. 미러를 피하려던 장치가 "V1까지만의 미러"가 된 것이다. 같은 결로 문서 세 곳이 `used_listing_observation`을 "DB 계약"이라 부르고 있었는데 **그 테이블은 V1·V2 어디에도 없다** — 계약이 아니라 계획이었다.
- 원인: 원본을 참조한다는 규율은 **"어느 원본을, 전부"**까지 정해야 완성된다. 한 파일을 지목하는 순간 그 지목이 곧 스냅샷이다.
- 규칙화된 교훈 (원인→해결): 원본을 참조할 땐 **범위를 열어 두고 정렬 규칙을 명시**한다 — `V*__*.sql`을 **버전 숫자순**으로(사전순이면 V10이 V2보다 앞선다) 전부 적용하고, 새 마이그레이션이 자동으로 포함되게 한다. 그리고 그 사실 자체를 테스트로 못박는다(`purchase` 테이블 존재 = V2가 적용됐다는 증거).
- 부수: 같은 커밋에서 관통 테스트를 추가했다 — golden fixture가 **실제로 낳는 값 전부**를 실 스키마에 붓는다. `ENDED` 사고(파서 GREEN·sink GREEN·경계에서 터짐)의 재발 방지선이다. 손으로 만든 레코드는 생산자의 값 범위를 대변하지 못한다.
- 관련 테스트: `tests/test_schema_fixture.py`(버전순 정렬·V2 적용 확인), `tests/test_end_to_end_ingest.py`(3사 골든 → 실 Postgres, status CHECK·한글 왕복·jsonb `_derived`).

### 2026-07-09 리허설의 대상은 서비스가 아니라 계약이다 — S3를 부르지 않고 S3 코드를 검증했다
- 맥락: REL-04의 마지막 조각인 오프사이트(S3) 사본. 실 AWS 호출은 비용·자격증명·정지조건에 걸린다. 그렇다고 "코드는 썼고 운영에서 확인하자"는 **검증되지 않은 백업**이다(같은 날 배운 교훈).
- 해법: 우리가 검증해야 하는 것은 AWS가 아니라 **"S3 API에 대고 우리 스크립트가 옳게 동작하는가"**다. MinIO(S3 호환)를 일회용 컨테이너로 띄우고 `offsite-upload.sh`를 **운영과 같은 코드 경로로** 실행했다. CI에 `offsite` 잡으로 걸어 매 커밋 돌린다.
- 설계: 업로드 뒤 `head-object`로 **원격 크기를 대조**한다 — "업로드 명령이 성공했다"와 "온전한 객체가 거기 있다"는 다른 사건이다. 스위치(`BACKUP_S3_BUCKET`)가 없으면 **조용히 성공하지 않고** "오프사이트 없음"을 출력한다(드릴이 그 문구까지 단언한다). aws-cli는 컨테이너로만 실행해 호스트를 더럽히지 않는다(OPS-02).
- 부수 교훈(내가 한 실수): 배선을 확인하려 만든 임시 테스트가 계속 실패하자 **스크립트를 의심했다.** 원인은 `MINIO_ROOT_USER=u1`이 3자 미만이라 MinIO가 기동조차 못 한 것이었다(`docker logs`가 그렇게 말하고 있었다). **테스트 하네스가 틀렸을 가능성을 대상 코드보다 먼저 배제하라** — 이미 통과한 드릴이 그 증거를 들고 있었다.
- 관련 테스트: `scripts/offsite-drill.sh`(업로드 → head 확인 → 왕복 gzip 무결성 → 미설정 경고 → 없는 키 head 실패), CI `offsite` 잡. 실측: `backup.sh` 전 경로가 MinIO 버킷에 394바이트 객체를 남겼다.

### 2026-07-09 롤백은 역순으로만 성립한다 — 그리고 그 사실은 드릴만이 안다
- 맥락: REL-05는 "마이그레이션마다 롤백 스크립트 동반"을 요구한다. `R1__init_rollback.sql`은 있었고 `R2`는 없었다. 더 중요한 건 **R1조차 한 번도 실행된 적이 없다**는 것이었다.
- 증상: `R2`를 쓰고 드릴을 돌리니 곧바로 드러났다. `purchase`(V2)가 `variant`·`deal_event`(V1)를 참조하므로 **R1을 먼저 돌리면 `drop table variant`가 "other objects depend on it"으로 멈춘다.** 그때 이미 `global_setting`·`review_queue_item`·`alert_policy`… 여덟 개 테이블이 지워진 뒤다. 즉 "롤백했다"고 믿는 순간 남는 건 **반쯤 부서진 DB**다.
- 원인: 롤백 스크립트는 사고가 나야 처음 실행되는 코드다. 그 코드에 대해 우리가 아는 것은 전부 **추측**이었다. 순서 의존은 파일을 읽어선 보이지 않는다 — 외래키는 두 파일에 걸쳐 있다.
- 규칙화된 교훈 (원인→해결): 리허설을 **네 갈래**로 만든다. ① 모든 `V<n>`에 짝이 되는 `R<n>`이 있는가(없으면 되돌릴 수 없는 마이그레이션이다) ② 역순 적용이 스키마를 **비우는가** ③ **순서를 어기면 실패하는가**(조용히 통과하면 반쯤 부서진 DB를 못 잡는다) ④ 롤백 뒤 **재전진**이 되는가(시퀀스·타입 잔여물). 그리고 드릴 자체를 시험한다 — `R2`를 잠시 숨겨 정말 FAIL하는지 봤다. 일회용 컨테이너·tmpfs·볼륨 없음은 기본이다.
- 부수: 같은 커밋에서 `written` 카운터를 고쳤다. sink가 있는데 딜이 0건이면 `written`이 **부재**했다 — 우리가 세운 "카운터에서 0을 생략하지 않는다"를 우리가 어기고 있었다. 이제 `written: 0`(0건 적재)과 부재 + `sink_error`(적재 못 함)가 갈린다.
- 관련 테스트: `scripts/rollback-drill.sh`(CI `rollback` 잡), `collector/tests/test_main.py`(0 적재 vs 적재 실패).

### 2026-07-09 날짜만 아는 시각을 `new Date(date)`로 만들면 하루를 통째로 잃는다
- 맥락: 구매 기록(PUR)의 `purchased_at`. V2 마이그레이션 주석이 "날짜만이면 23:59 KST는 **입력 계층**"이라고 계약을 못박아 뒀다. 웹이 그 입력 계층이다.
- 증상(예방된 것): `new Date('2026-07-01')`은 **UTC 자정**으로 해석된다(`2026-07-01T00:00:00Z` = KST 09:00). 그대로 보내면 as-of 스냅샷이 하루 앞의 세계를 본다 — 그날 낮에 뜬 딜이 전부 창 밖으로 밀린다. 반대로 `new Date('2026-07-01T23:59')`는 **실행 머신의 타임존**으로 해석돼, 서버·CI·사용자 기기가 다르면 값이 달라진다.
- 원인: JS의 `Date` 파싱은 문자열 모양에 따라 UTC/로컬을 조용히 갈아탄다. 둘 다 "동작하는 것처럼" 보인다.
- 규칙화된 교훈 (원인→해결): **오프셋을 문자열에 박아 넣는다**(`${date}T23:59:00+09:00`). 그러면 어느 머신에서 돌려도 같은 Instant다. 그리고 테스트는 "기대값과 같다"만이 아니라 **"naive 파싱과는 다르다"**를 함께 단언한다(`expect(built).not.toBe(new Date(date).toISOString())`) — 우연히 같아지는 구현을 걸러낸다.
- 부수: 컴포넌트를 다른 화면에 합성하자 기존 테스트 하나가 깨졌다. `PurchasePanel`이 스텁되지 않은 `listPurchases`를 불러 **두 번째 `role="alert"`**를 만든 것이다. 합성은 부모의 테스트 하네스도 넓힌다 — 자식이 무엇을 부르는지가 계약의 일부다.
- 관련 테스트: `web/src/purchase/buildPurchaseCommand.test.ts`, `scripts/smoke.sh` 5-2단계(구매 POST → `OBSERVING`·`NO_ACTIVE_DEAL`·`cheaperChanceCount:0` 왕복).

### 2026-07-09 "바이트 그대로"라고 적어둔 golden을 Windows와 CI가 다른 바이트로 읽고 있었다
- 맥락: `.gitattributes`가 없었고 `core.autocrlf=true`였다. golden fixture 문서에는 "핫딜 사이트의 응답을 **바이트 그대로** 보관한다"라고 적혀 있었다.
- 증상(실측): 워킹트리 vs blob — `bunjang/find_v2_iphone.json` 34,422B vs 33,439B(**+983 CR**), `fmkorea/list_normal.html` 85,840B vs 85,462B(**+378 CR**). 체크아웃이 LF→CRLF로 부풀린 것이다. 같은 파서 테스트가 **Windows에선 CRLF, 리눅스 CI에선 LF**를 읽고 있었고, 파서가 공백에 관대해 아무도 몰랐다. 뽐뿌·루리웹은 blob에 홑 CR이 섞여 있어(21·777개) git이 변환을 건너뛰었다 — 우연히 살아남은 것이다.
- 원인: 줄끝은 **저장소가 정하지 않으면 각자의 `core.autocrlf`가 정한다.** 그러면 "저장소가 진실"이라는 전제가 무너진다. 커밋 시 CRLF→LF 정규화도 함께 일어나므로, 원본이 CRLF였다면 골든은 채취 순간 이미 원본이 아니었다(복구 불가 — 지금 blob이 정본).
- 규칙화된 교훈 (원인→해결): **바이트가 의미인 파일은 `-text`로 못박는다**(`collector/tests/fixtures/** -text`). 나머지는 `* text=auto eol=lf`로 플랫폼이 사실을 바꾸지 못하게 한다. 실행되는 것(`*.sh`, 훅)은 CRLF 셰뱅이 리눅스에서 죽으므로 `eol=lf`가 필수다. 그리고 **그 장치가 도는지 아무도 확인하지 않는다** — golden의 sha256을 테스트로 동결하고, 바이트 하나를 바꿔 실제로 FAIL하는지 봤다.
- 곁가지: shellcheck를 붙이니 첫 출력이 `SC1017 literal carriage return` 도배였다. 린터가 못 도는 이유가 곧 버그의 증거였다. CRLF를 걷어내자 실질 지적은 `SC1007`(빈 env 접두사) 하나뿐이었고, 이제 CI `lint` 잡이 `--severity=warning`으로 상시 검사한다.
- 관련 테스트: `collector/tests/test_fixture_bytes.py`(해시 동결 + 미등록 fixture 탐지, 음성 확인 완료), CI `lint` 잡, `.gitattributes`.

### 2026-07-09 트리 전체에 `tr -d '\r'`를 돌려 gradle-wrapper.jar를 160바이트 깎았다
- 맥락: `.gitattributes`를 넣어 줄끝을 못박은 뒤, **워킹트리에 이미 남아 있던 CRLF도 지금 걷어내자**고 판단했다. `git ls-files`를 돌며 CR이 있는 파일마다 `tr -d '\r'`을 먹였다. fixture 디렉토리만 제외했다.
- 증상: 236개 파일이 바뀌었고 그중 `core/gradle/wrapper/gradle-wrapper.jar`가 43,764 → **43,604바이트**가 됐다. 바이너리 안의 `0x0D`는 줄끝이 아니라 데이터다. `core/.gitattributes`가 `*.jar binary`라고 선언해 뒀는데, 내 루프는 그걸 읽지 않았다.
- 원인 둘: ① **git의 속성 체계를 우회해 파일을 직접 건드렸다.** 텍스트/바이너리 판정은 git이 이미 알고 있고 `.gitattributes`에 적혀 있는데, `tr`은 아무것도 모른다. ② 애초에 **불필요한 작업**이었다. `git diff`는 그 235개 텍스트 파일에 대해 "차이 없음"이라 말했다 — blob은 이미 LF였고 워킹트리 CRLF는 clean 필터가 흡수하고 있었다. 고칠 게 없는데 고치려다 부쉈다.
- 복구: 손상은 jar 하나뿐임을 `git diff --numstat`으로 확인(나머지는 stat 캐시만 흔들림)하고 그 한 파일만 `git checkout --`으로 되살렸다. 검증은 "바이트가 blob과 같다"에서 멈추지 않고 **zip 엔트리 33개·`GradleWrapperMain` 존재·`./gradlew --version` 실행**까지 봤다. 인덱스는 `git add --renormalize .`로 정리(스테이징된 내용 변화 0).
- 규칙화된 교훈 (원인→해결): **저장소 전체를 훑는 텍스트 변환을 직접 하지 않는다.** 줄끝은 `.gitattributes` + `git add --renormalize` + 체크아웃에 맡긴다 — git은 무엇이 바이너리인지 안다. 손으로 돌려야 한다면 **대상을 화이트리스트로 열거**하고(`*.sh` 등) 절대 `git ls-files` 전체를 먹이지 않는다. 그리고 파괴적 일괄 작업 전에 **"정말 고칠 게 있는가"를 `git diff`로 먼저 묻는다.**
- 곁가지: 권한 가드가 `git checkout -- .`과 `git ls-files -m | xargs git checkout --`을 **막았다.** 그 덕에 "복구"라는 이름으로 트리 전체를 덮어쓰는 대신, 실제 손상 파일을 특정하고 한 개만 되돌렸다. 광범위한 되돌리기도 파괴다.
- 관련 테스트: 없음(작업 절차). `collector/tests/test_fixture_bytes.py`가 golden 쪽 재발은 잡는다.

### 2026-07-09 훅을 만들고 "있다"고 적었지만, 도는지 시험하기 전까지는 없는 것과 같다
- 맥락: `.githooks/pre-commit`(gitleaks `protect --staged`)은 며칠 전에 만들어 `docs/91` Q-42에 "미활성"이라고 적어뒀다. 그리고 바로 그 훅이 잡았어야 할 오탐이 CI에서 터졌다.
- 증상: 훅을 처음 실행해봤다. 첫 미끼로 AWS 예제 키(`wJalrXUtnFEMI/...EXAMPLEKEY`)를 스테이징했는데 **통과**했다. 훅이 고장 난 줄 알았지만, 원인은 gitleaks가 문서용 예제 키를 기본 allowlist로 걸러낸 것이었다 — **또 하네스가 틀렸다.** 우리가 실제로 잡히는 걸 확인한 패턴(`curl -u admin:...`)으로 바꾸자 정확히 exit 1로 막았다.
- 원인: 훅·가드·헬스체크처럼 "평소에는 아무 일도 안 하는 장치"는 만든 순간부터 **동작한다는 가정**으로 문서에 적히고, 아무도 반례를 만들지 않는다.
- 규칙화된 교훈 (원인→해결): **차단 장치에는 계약 테스트를 붙인다** — 일회용 저장소에서 ① 진짜 시크릿을 막는가 ② 승인된 예외를 통과시키는가 ③ 평범한 변경을 통과시키는가 ④ 예외가 좁은가(같은 문자열·다른 파일이면 막는가). 오차단은 조용히 작업을 마비시키므로 **통과 쪽을 더 많이** 시험한다. 그리고 미끼는 **이미 잡히는 걸 확인한 값**으로 만든다 — 스캐너의 기본 allowlist가 예제 키를 걸러내면 시험 자체가 무의미하다.
- 관련 테스트: `.githooks/pre-commit.test.sh`(4갈래, CI `secrets` 잡). `docs/91` Q-42 갱신.

### 2026-07-09 보드에 없는 요구는 "없는 일"이 된다 — "다 했다"고 두 번 보고했다
- 맥락: "사람 결정·외부 키 없이 할 수 있는 건 다 했다"고 보고했다. 근거는 `docs/91`(기술 보류)·`working-area/*`(결정·배포)의 열린 항목이 전부 HUMAN/KEY/NET/CORE로 막혀 있다는 것이었다.
- 증상: 사용자가 "다시 한번 확인해보자"고 해서 **요구 문서(`docs/20` 비기능)를 처음부터 다시 읽었다.** 세 개가 어느 보드에도 없었다. ① SEC-06(Dependabot·이미지 고정) — `.github/dependabot.yml`이 없고 `collector/Dockerfile`이 `uv:latest`를 빌드 산출물에 섞고 있었다. ② OPS-03(저장 UTC, **표시 KST**) — `PurchasePanel`이 `purchasedAt.slice(0,10)`으로 UTC 날짜를 그려 하루가 어긋날 수 있었다. ③ SEC-05(크기 상한) — 아예 없었다.
- 원인: **보드가 곧 시야다.** `SessionStart` 훅이 주입하는 것은 `decisions-needed`와 `docs/91`뿐이다. 요구 문서에 있지만 보드에 옮겨적지 않은 항목은 브리핑에 뜨지 않고, 그래서 "열린 것이 없다"가 "할 일이 없다"로 둔갑한다. 구현하지 않기로 한 것조차 **적어두지 않으면 잊힌다.**
- 규칙화된 교훈 (원인→해결): **"남은 게 없다"는 열린 보드가 아니라 요구 문서와 대조해서 말한다.** 요구 ID(SEC/PERF/REL/OBS/OPS)를 전수로 훑어 코드·CI에 대응물이 있는지 확인하고, 없으면 **구현하거나 보드에 올린다**(Q-55처럼 "안 한다"도 근거와 재개 트리거를 달아 올린다). 보드는 열린 것의 목록이 아니라 **시야의 경계**다.
- 곁가지: 같은 감사에서 `.claude/rules/collector-python.md`가 이미 고친 버그를 경고하고 있었다("`parse_bunjang`은 `ENDED`를 낸다" — 2026-07-09 해소). **컨텍스트에 자동 로드되는 규칙 파일이 거짓말하면 다음 세션이 고친 버그를 또 고친다.** 규칙 파일은 코드보다 먼저 정정한다.
- 관련 테스트: `web/src/purchase/present.test.ts`(`kstDate` 경계값 — `2026-07-01T20:00:00Z` → `2026-07-02`), `.github/dependabot.yml`(schemastore 스키마 검증 PASS).

### 2026-07-10 쓰는 쪽도 읽는 쪽도 GREEN인데, 둘을 잇는 트리거가 없어 시스템이 죽어 있었다
- 맥락: collector는 `raw_deal_post`에 업서트한다(통합 테스트 GREEN). core는 `ingestPending()`으로 그걸 읽어 `deal_event`를 만든다(Testcontainers GREEN). 상대는 `reprocessEndedDeals()`까지 붙였다(4케이스 GREEN). 종단 스모크도 PASS였다.
- 증상: **프로덕션에서 `ingestPending()`을 부르는 곳이 한 군데도 없었다.** `grep -rn "@Scheduled\|@EnableScheduling\|ApplicationRunner" core/src/main/java` → 0건. `adapter/scheduler/`엔 `package-info.java` 하나뿐이고 javadoc은 `/** 폴링·파이프라인 트리거 */`라고만 적혀 있었다. 즉 `docker compose up -d`로 스택을 띄워도 **기준가 표본은 영원히 0이고 알림은 영원히 안 온다.** M1을 막던 진짜 블로커는 텔레그램 토큰도 네이버 키도 아니었다.
- 원인: 모든 테스트가 "이 함수는 옳게 동작하는가"를 물었고, **"프로덕션에서 이 함수를 누가 부르는가"는 아무도 묻지 않았다.** 단위 테스트는 직접 부르고, 통합 테스트도 직접 부르고, 스모크는 REST만 두드렸다. 빈 패키지와 javadoc이 "여기 트리거가 있다"고 착각하게 만들었다.
- 규칙화된 교훈 (원인→해결): **진입점(entry point)을 테스트한다.** "이 유스케이스를 프로덕션에서 부르는 경로가 존재하는가"는 별도의 질문이고 별도의 테스트가 필요하다. ① 배선 테스트: `@EnableScheduling`이 빠지면 `@Scheduled`는 **조용히 무시된다** — `ScheduledTaskHolder.getScheduledTasks()`로 **등록 사실**을 단언한다(애노테이션 존재가 아니라). ② 종단 스모크: 계약 테이블에 행을 하나 넣고 **REST 응답이 바뀌는지** 본다(`tier: NONE → SPARSE`). 그러면 "누가 부르는가"가 자동으로 검증된다. ③ 그 배선 테스트도 시험한다 — `@EnableScheduling`을 잠시 떼어 실제로 FAIL하는지 봤다.
- 곁가지: 새 스모크가 처음에 실패했을 때 원인은 스케줄러가 아니라 **내 스모크의 greedy `sed`**였다(`sed 's/.*"variantId"...'`가 **마지막** 것을 집어, 딜이 붙은 256GB 대신 512GB를 조회했다). 격리 스택을 띄워 DB를 직접 보고 나서야 갈랐다 — `deal_event`는 이미 만들어져 있었다. **대상 코드보다 하네스를 먼저 의심하라**(같은 실수를 MinIO·gitleaks에서 이미 두 번 했다).
- 관련 테스트: `PipelineSchedulerTest`(순서·단계별 예외 격리), `PipelineSchedulerWiringTest`(스케줄 등록 사실), `scripts/smoke.sh` 5-1b(`raw_deal_post` → `deal_event` → 기준가 REST).

### 2026-07-10 상한을 넘긴 입력은 자르는 게 아니라 거절한다 — 잘린 값은 정상 값의 얼굴을 한다
- 맥락: SEC-05("크롤링 텍스트는 전부 비신뢰 입력 … 크기 상한")를 구현했다. 첫 충동은 `title[:300]`이었다.
- 왜 안 되나: 잘린 제목은 **여전히 제목처럼 생겼다.** 매칭(BM-03)은 그걸 정상 입력으로 받아 별칭 substring을 놓치고, 가격 파싱은 뒤쪽에 있던 `(999,000원/무료)`를 통째로 잃는다. 결과는 "가격 없음 → 스킵"이라 **조용하다.** 반면 거절하면 그 딜은 없는 것이고, 이벤트가 남아 사람이 원문을 볼 수 있다. 이건 이미 배운 규칙의 다른 얼굴이다 — **"값 없음"을 값으로 표현하지 않는다.**
- 규칙화된 교훈 (원인→해결): **상한은 자르는 장치가 아니라 거르는 장치다.** ① 넘으면 **버리고**, 무엇을·왜(필드·크기·상한)를 이벤트로 남긴다. ② 한 건이 비대해도 **배치 전체를 버리지 않는다**(원칙 3: 놓침 > 오알림 — 68건까지 잃을 이유가 없다). ③ 상한값은 **골든 전수 실측 최대의 수 배**로 잡고 **오차단 0건을 대조로 증명**한다(차단 장치는 "통과시켜야 할 것"을 먼저 시험한다). ④ 텍스트 상한은 글자 수, **원본(jsonb) 상한은 바이트** — 한글은 UTF-8에서 3바이트라 글자 수로 재면 상한이 3배로 뚫린다.
- 관련 테스트: `tests/test_ingest.py`(골든 실측 크기는 통과 / 초과는 거절·미절단 / 배치 생존 / 바이트 단위 raw), `tests/test_main.py`(`oversized` 이벤트 + `cycle.skipped`는 0도 센다).

### 2026-07-10 조용히 도는 스케줄러는 죽은 스케줄러와 구별되지 않는다
- 맥락: 파이프라인 트리거를 붙이고 스모크로 "딜이 생긴다"를 증명했다. 그런데 그 스케줄러는 매 틱 **아무 말도 하지 않았다.**
- 증상(예방된 것): 매칭이 전부 REJECTED로 빠지거나 `findUnprocessed()`가 영원히 같은 행을 되돌려줘도 로그는 조용하다. collector에서 이미 겪은 "성공했는데 0건"(REL-06)과 같은 실패 유형인데, core에는 그걸 볼 눈이 없었다. OBS-02가 요구하는 카운터도 **어느 보드에도 없었다.**
- 원인: 스케줄러의 성공 신호가 "예외가 안 났다"뿐이었다. 예외 없이 아무것도 안 하는 것이 가장 흔한 실패다.
- 규칙화된 교훈 (원인→해결): **주기 작업은 매 틱 무엇을 했는지 수치로 남긴다.** ① 절대 수가 아니라 **전후 스냅샷의 차이**를 낸다 — 직접 셀 수 없는 값도 유도된다(`merged = postsLinked − dealsCreated`: 링크는 늘었는데 딜이 안 늘었으면 흡수된 것이다). ② **`pending`(처리되지 않고 남은 입력)을 반드시 포함한다** — 단조 증가하면 도는 척하는 것이다. ③ **0을 생략하지 않는다.** ④ 로그 문구를 테스트하지 말고 **보고서를 값으로 뽑아** 순수 테스트한다(`Consumer<Report>` seam) — 문구는 바뀌어도 계약은 안 바뀐다. ⑤ 단계가 터져도 보고는 낸다. 그때 "무엇이 남았는지"가 더 중요하다.
- 관련 테스트: `PipelineTickReportTest`(차이 산술·병합 유도·유휴 틱의 0), `PipelineSchedulerTest`(전후 두 번 찍는다 / 실패해도 보고한다), `scripts/smoke.sh` 5-1b(`dealsCreated=1 merged=0 pending=0`을 실 로그에서 확인).

### 2026-07-10 "지금 가격"은 가장 최근 관측이 아니라 가장 최근 **살 수 있는** 관측이다
- 맥락: BM-01 AC-2("가격 변경은 기존 행에 반영")를 구현했다. 딜 하나에 여러 원문(사이트)이 링크될 수 있다.
- 함정: `priceLast`를 "capturedAt이 가장 큰 원문의 가격"으로 정의하면, **품절된 원문이 가장 최근에 관측됐을 때 그 가격이 "지금"이 된다.** 800,000원짜리가 방금 품절됐는데 화면은 "지금 800,000원"이라 말한다 — 살 수 없는 가격이다. 반대로 그 800,000원을 `priceMin`("지나간 기회")에서 지우면 "한때 그 값에 살 수 있었다"는 사실을 잃는다.
- 원인: 가격 역할 3분법(docs/02: `priceFirst`=발생·분포 / `priceLast`="지금" / `priceMin`="지나간 기회" / `priceMax`=역할 없음)은 **각 필드가 답하는 질문이 다르다**는 뜻인데, 하나의 정렬 기준으로 전부 채우려 했다.
- 규칙화된 교훈 (원인→해결): **필드마다 증거의 자격 요건이 다르다.** "지금"의 후보는 **활성 원문뿐**이고, "지나간 기회"의 후보는 **모든 원문**이다. 그리고 `priceFirst`는 어떤 재처리에서도 움직이지 않는다 — 기준가 median·percentile이 그 위에 서 있으므로 한 번 흔들면 분포 전체가 거짓이 된다. 부수: `lastSeen`은 단조(늦게 도착한 과거 관측이 시계를 되돌리면 안 된다), 그리고 **바뀐 게 없으면 쓰지 않는다**(빈 갱신은 lastSeen만 흔들어 "언제 마지막으로 실제로 변했는가"를 잃는다).
- 배치 순서: 스케줄러는 ingest → **가격** → 종료 순이다. 종료가 마지막이라 딜이 닫히기 직전의 마지막 가격까지 반영된다. 반대 순서였다면 종료된 딜의 마지막 가격 변동을 영원히 놓친다.
- 관련 테스트: `PriceRefreshTest`(9건, 순수 산술 — 품절 원문은 "지금"의 후보가 아니다 / 모두 품절이면 priceLast 불변 / lastSeen 단조 / 동시각이면 더 싼 쪽), `ReprocessDealPricesUseCaseTest`(6건, Testcontainers 이음새), `scripts/smoke.sh` 5-1c(`999000/899000/899000`).
