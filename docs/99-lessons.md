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
