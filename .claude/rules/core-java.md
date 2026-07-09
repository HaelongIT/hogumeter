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
- **Testcontainers 2.0 좌표·패키지가 바뀌었다**: 아티팩트는 `testcontainers-` 접두사(`testcontainers-junit-jupiter`·`testcontainers-postgresql`), 클래스는 `org.testcontainers.postgresql.PostgreSQLContainer`, self-type 제네릭 제거(`PostgreSQLContainer<?>` 아님). (99: 2026-07-04)
- **`ddl-auto=validate`는 DDL 타입과 JPA 필드 타입을 정확히 맞춘다.** `smallint` 컬럼을 `Integer`로 매핑하면 검증 실패. `@JdbcTypeCode(SqlTypes.SMALLINT)`를 붙이거나 필드를 `Short`로. **컨텍스트 로드가 무더기로 깨지면 스키마 검증 불일치를 먼저 의심**하고 리포트에서 `Schema-validation` 라인을 볼 것. (99: 2026-07-08)
- **`@SpringBootTest`는 컨테이너(postgres)를 공유하고 기본은 롤백이 없다.** 전역 count 단정은 다른 테스트의 커밋에 오염된다 → 특정 `variantId`로 스코프하거나 `@Transactional`로 tx 롤백. (99: 2026-07-08)

## 도메인 규칙 (교훈에서 승격)

- **윈도우 확장은 "표본이 실제로 증가했을 때만" 유효 범위·표기를 갱신한다**(`wider.size() > sample.size()` 가드). 그래야 "과거 딜 없음 → 확장 무발동(null)"이 성립해 경계 테스트가 tier만 순수 격리한다. 상한(12개월) 밖 딜은 어떤 경우도 미포함. BM-04 병합 윈도우·BM-05 표본 윈도우에도 같이 적용. (99: 2026-07-04)

> TDD·순수 도메인·파라미터 주입·Flyway 소유권 규율은 CLAUDE.md에 있다. 여기 옮겨 적지 않는다 — 중복 지침은 준수율을 떨어뜨린다.
