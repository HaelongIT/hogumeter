# core — 기준가 엔진·매칭·알림·REST (Spring Boot)

hogumeter의 상시 가동 코어. **헥사고날** 구조로, 순수 `domain`을 얇은 `adapter`가 감싼다. TDD의 성패는 도메인의 순수성에 달려 있다.

- 스택: **Spring Boot 4.1 · Java 21 · Gradle(Kotlin DSL) · JUnit 5 · Testcontainers**
- 상세 경계: [`../docs/01-architecture.md`](../docs/01-architecture.md) · TDD 규율: [`../docs/21-tdd-guidelines.md`](../docs/21-tdd-guidelines.md)

## 빌드·테스트

```bash
./gradlew test      # 단위 + @DataJpaTest(Testcontainers PostgreSQL 16)
./gradlew bootRun   # 로컬 실행 (DB 필요 — 루트에서 docker compose up -d postgres)
```

## 패키지 구조

```
dev.hogumeter.core
├── domain/          # 순수 Java. Spring/JPA/IO 의존 금지. 단위 테스트로 완결
│   ├── product/     # Product, Variant, 축 모델
│   ├── deal/        # DealEvent, 병합 판정, 상태기계(전이 규칙)
│   ├── benchmark/   # 기준가 엔진: 정규화·이상치 판정·median/P25·3단 표본
│   ├── matching/    # 문자열 정규화·별칭 사전 매칭·3단 판정
│   ├── alert/       # 트리거 평가·최고강도 1발·방해금지 보류·후속
│   └── review/      # 승격 큐(미상 분류·이상치 승격·키워드 제안)
├── application/     # 유스케이스 오케스트레이션 + port 인터페이스
│   └── port/ (in: usecase / out: repository·naverClient·telegramSender·clock)
└── adapter/
    ├── persistence/ # JPA (@DataJpaTest + Testcontainers)
    ├── web/         # REST 컨트롤러 (@WebMvcTest)
    ├── telegram/    # 봇 어댑터 (발송 + 인라인 버튼 콜백)
    ├── naver/       # 네이버 쇼핑 API 클라이언트 (WireMock 테스트)
    └── scheduler/   # 알림 평가·방해금지 플러시·캐시 만료
```

> 2차 기획 도메인 모듈(`purchase`·`digest`·`watch`·`priority`)은 M5/M6 착수 시 추가 — [`../docs/01-architecture.md`](../docs/01-architecture.md), [`../docs/15-feature-purchase.md`](../docs/15-feature-purchase.md) 등.

## 규칙 (지킬 것)
- `domain`은 프레임워크 애노테이션을 갖지 않는다. 시간은 `Clock` 주입, 난수 금지.
- 모든 판정 로직은 "입력 → 결과" 순수 함수 시그니처. 예: `AlertDecision decide(DealEvent, AlertPolicy, BenchmarkView)`.
- 수치 파라미터는 하드코딩 금지 — `BenchmarkParams`(seam) 주입만 참조([`../docs/31-detailed-params.md`](../docs/31-detailed-params.md)).
- `adapter`는 얇게. 로직이 스며들면 리팩토링으로 `domain`에 밀어 넣는다.

## DB
- Flyway 마이그레이션은 **core 단독 소유**(`src/main/resources/db/migration`). collector는 계약 테이블만 접근.
- 통합 테스트는 Testcontainers PostgreSQL 16. 순수 도메인은 DB 없이 단위 테스트.
