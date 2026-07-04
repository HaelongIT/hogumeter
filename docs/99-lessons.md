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
