---
paths:
  - "core/**/*.java"
  - "core/**/*.kts"
  - "core/**/*.sql"
---

# core (Spring Boot 4.1 · Java 21) 함정 — 실측으로 얻은 규칙

> 전문은 `docs/99-lessons.md`. 여기엔 한 줄 규칙만 둔다(중복 금지).
> 이 파일은 `core/` 파일을 열 때만 컨텍스트에 로드된다 — collector 작업 시엔 비용 0.

- **Boot 메이저 이관 시 "starter로 교체"는 방언·DB 전용 모듈까지 옮겨주지 않는다.** `spring-boot-starter-flyway`는 flyway-core만 준다 — `flyway-database-postgresql`을 따로 명시. 런타임 GREEN까지 확인할 것. (99: 2026-07-04 Boot 4.1 이관)
- **Boot 4는 슬라이스 테스트 자동설정을 모듈별 스타터로 분리**했다. `@WebMvcTest`/`@AutoConfigureMockMvc`를 쓰면 `spring-boot-starter-webmvc-test`를 추가하고 import를 `org.springframework.boot.<module>.test.autoconfigure`로 고친다. 못 찾으면 gradle 캐시의 **실제 4.x jar**에서 패키지 경로를 확인(3.5.x jar의 옛 경로에 낚이지 말 것). (99: 2026-07-08)
- **Boot 4는 Jackson 3다** — `tools.jackson.databind.ObjectMapper`·`tools.jackson.core.type.TypeReference`. 애노테이션만 `com.fasterxml.jackson.annotation`(2.x)에 남아 있어 `@JsonInclude`가 컴파일된다고 `com.fasterxml.jackson.databind`가 있는 게 아니다. `JacksonException`은 unchecked. 좌표는 `./gradlew dependencies`, 클래스 경로는 jar `unzip -l`로 확인한다. (99: 2026-07-10)
- **Testcontainers 2.0 좌표·패키지가 바뀌었다**: 아티팩트는 `testcontainers-` 접두사(`testcontainers-junit-jupiter`·`testcontainers-postgresql`), 클래스는 `org.testcontainers.postgresql.PostgreSQLContainer`, self-type 제네릭 제거(`PostgreSQLContainer<?>` 아님). (99: 2026-07-04)
- **`ddl-auto=validate`는 DDL 타입과 JPA 필드 타입을 정확히 맞춘다.** `smallint` 컬럼을 `Integer`로 매핑하면 검증 실패. `@JdbcTypeCode(SqlTypes.SMALLINT)`를 붙이거나 필드를 `Short`로. **컨텍스트 로드가 무더기로 깨지면 스키마 검증 불일치를 먼저 의심**하고 리포트에서 `Schema-validation` 라인을 볼 것. (99: 2026-07-08)
- **`@SpringBootTest`는 컨테이너(postgres)를 공유하고 기본은 롤백이 없다.** 전역 count 단정은 다른 테스트의 커밋에 오염된다 → 특정 `variantId`로 스코프하거나 `@Transactional`로 tx 롤백. (99: 2026-07-08)

## 도메인 규칙 (교훈에서 승격)

- **윈도우 확장은 "표본이 실제로 증가했을 때만" 유효 범위·표기를 갱신한다**(`wider.size() > sample.size()` 가드). 그래야 "과거 딜 없음 → 확장 무발동(null)"이 성립해 경계 테스트가 tier만 순수 격리한다. 상한(12개월) 밖 딜은 어떤 경우도 미포함. BM-04 병합 윈도우·BM-05 표본 윈도우에도 같이 적용. (99: 2026-07-04)

## 스케줄러 (`adapter/scheduler`)

- **`@EnableScheduling`이 없으면 `@Scheduled`는 조용히 무시된다.** 에러도 로그도 없고 그냥 안 돈다. 애노테이션 존재가 아니라 **등록 사실**을 단언하라 — `ScheduledTaskHolder.getScheduledTasks()`에 그 메서드가 있는가(`PipelineSchedulerWiringTest`). `sleep`으로 실행을 기다리지 않는다. (99: 2026-07-10)
- **`fixedDelay`는 기본적으로 기동 즉시 1회 돈다.** 그러면 `@SpringBootTest`가 스케줄러에 오염된다(컨테이너 공유 + 롤백 없음). `initialDelay = interval`로 미루고, `src/test/resources/application.properties`가 `core.pipeline.enabled=false`로 전역 차단한다(배선 테스트만 `properties`로 되켬).
- **주기 작업은 매 틱 무엇을 했는지 수치로 남긴다**(OBS-02). 전후 스냅샷의 **차이**를 내고 `pending`(처리되지 않고 남은 입력)을 포함한다 — 단조 증가하면 도는 척하는 것이다. 로그 문구를 테스트하지 말고 `Consumer<Report>` seam으로 **값**을 시험한다.
- **카운터는 오염되지 않는 쪽을 센다.** `purchasesExpired`를 `OBSERVING` 감소분으로 세면 틱 도중 REST로 들어온 새 구매가 값을 망친다. **스케줄러만 늘리는 쪽**(`REPORT_PENDING` 증가분)을 센다. (99: 2026-07-10) — **그런데 같은 루프에 그 큐를 비우는 스텝(성적표 발급: `REPORT_PENDING`→`CLOSED`)을 더하면 채움-델타조차 오염된다**(`Δ = 만료 − 발급`, 음수 가능). 비움 수를 스텝 반환(`Supplier<Integer>`)으로 직접 세어 리포트에 싣고, 채움 수를 `Δ + 비움`으로 재구성한다. **한 큐를 채우고 비우는 두 스텝이 같은 루프에 있으면 어느 델타도 순수한 카운터가 아니다.** (99: 2026-07-21)
- **단계 순서는 계약이다.** 관찰 만료(PUR-01)는 `ingest`보다 **먼저** 돈다 — ingest가 새 딜마다 알림을 태우는데 PUR-03 "산 뒤 알림"은 `OBSERVING`에만 발화한다. 순서를 뒤집으면 이미 끝난 관찰이 한 번 더 알림을 낸다. 순서를 테스트로 못박는다.
- **`try/catch`로 감싼 줄만 격리된다.** 스냅샷 조회도 DB를 탄다 — `runStep` 밖에 두니 DB 단절 시 첫 줄에서 터져 **단계가 한 번도 시도되지 않았고**, 예외를 삼키는 건 Spring이라 우리 로그엔 흔적도 없었다. 격리 장치를 만들면 **그 바깥에 남은 IO를 세어 본다**. (99: 2026-07-10)

## JPA 쓰기

- **미매핑 컬럼이 있는 테이블의 갱신을 delete+insert로 하지 않는다.** 엔티티가 모르는 컬럼(현재 `alert_policy.demand_axis_filter` — `k_display`·`exclude_keywords`는 소비 기능이 생겨 매핑됐다)이 DB 기본값으로 조용히 되돌아간다 — 지금은 아무도 안 써서 아무도 모르고, 누군가 매핑을 붙이는 날 데이터가 사라진다. 벌크 UPDATE로 **아는 컬럼만** 건드리고 "미매핑 컬럼이 살아남는다"를 테스트로 못박는다. (99: 2026-07-10)
- **벌크 UPDATE는 영속성 컨텍스트를 우회한다** — 같은 트랜잭션에서 그 행을 다시 읽으면 캐시된 옛 값이 나온다. `EntityManager.clear()`는 남의 엔티티까지 날리므로 해당 엔티티만 `refresh()`한다.
- **엔티티가 매핑하지 않는 컬럼은 네이티브 SQL로 다룬다** — 남의 엔티티를 고치지 않고 진실에 닿는 길이다. `GetReviewQueueUseCase`(읽기: `status`·`created_at`)와 `PreserveAppliedConditionsUseCase`(쓰기: `applied_conditions`)가 같은 수법이다. **"상대 소유라 막혔다"고 적기 전에 이 길을 먼저 시도한다** — Q-46·Q-48·Q-50이 전부 그렇게 잘못 봉인돼 있었다. (99: 2026-07-10)
- **주기적으로 도는 UPDATE는 멱등해야 하고, 그 비교 키가 로케일을 타면 안 된다.** `array(... order by tag)`의 기본 정렬은 서버 로케일이 정한다(postgres:16 실측: 한글이 코드포인트 순서와 다르게 나온다). 배열을 `is distinct from`으로 비교하면 로케일이 다른 DB에서 매 틱 UPDATE가 돈다 — `collate "C"`로 바이트 순서를 못박는다. (99: 2026-07-10)
- **에러코드는 개념 단위로 재사용한다.** 알림 정책의 기간 P는 기준가의 그것과 같은 값이라 `BM_INVALID_PERIOD`를 그대로 쓴다 — 같은 개념에 코드를 둘 만들면 클라이언트 분기가 둘로 갈린다(`docs/benchmark/07`).

## 헬스·DB (`adapter/web/HealthController`)

- **헬스 응답에 예외 메시지를 싣지 않는다** — JDBC 예외 메시지는 접속 URL·사용자명을 담고 헬스는 인증 없이 노출된다. **예외 타입 이름만**(`SQLException`). 관측이 유출이 되면 안 된다(SEC-01).
- **죽은 DB 앞에서 `getConnection()`은 Hikari `connectionTimeout`(기본 30s)만큼 매달린다.** compose에서 3초로 좁혀 뒀다 — 헬스는 빨리 실패해야 "무엇이 죽었나"에 답할 수 있다. 빈 컴포넌트 집합의 `allMatch`는 `true`이므로 `HealthReport.of({})`는 예외를 던진다.

> TDD·순수 도메인·파라미터 주입·Flyway 소유권·**모듈 소유권(core=상대, 신규 파일 additive만 자율)**은 CLAUDE.md에 있다. 여기 옮겨 적지 않는다 — 중복 지침은 준수율을 떨어뜨린다.
