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

## 파이프라인 트리거 (`adapter/scheduler`)

**유스케이스가 옳게 동작하는 것과, 프로덕션에서 누군가 그걸 부르는 것은 다른 문제다.** 2026-07-10까지 `ingestPending()`을 부르는 곳이 하나도 없어 `deal_event`가 생기지 않았다(`docs/91` Q-27 ⑤).

`PipelineScheduler.tick()`이 `core.pipeline.interval-ms`(기본 60초)마다 **ingest → 가격 → 종료** 순으로 돈다.

- **종료가 마지막**이라 딜이 닫히기 직전의 마지막 가격까지 반영된다.
- **단계별 예외 격리**: 하나가 터져도 뒤 단계·다음 주기는 산다. 뭉개지 않고 단계명과 함께 `log.error`(Q-56).
- **`initialDelay = interval`**: 기동 즉시 돌지 않는다. `fixedDelay`는 기본적으로 시작하자마자 1회 실행되는데, 그러면 `@SpringBootTest`들이 오염된다. 테스트는 `src/test/resources/application.properties`가 `core.pipeline.enabled=false`로 전역 차단하고, 배선 테스트만 되켠다.
- **매 틱 `PipelineTickReport`**(OBS-02): `postsLinked·dealsCreated·merged·queued·ended·pending·rawTotal`. 0을 생략하지 않는다 — 조용히 도는 스케줄러는 죽은 스케줄러와 구별되지 않는다.
- 로그는 **JSON(ECS)**. `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`를 compose가 넘긴다(OBS-01) — core 파일은 건드리지 않는다.

`@EnableScheduling`이 빠지면 `@Scheduled`는 **조용히 무시된다.** 그래서 `PipelineSchedulerWiringTest`는 애노테이션 존재가 아니라 `ScheduledTaskHolder`에 태스크가 **등록됐는지**를 단언한다.

## 규칙 (지킬 것)
- `domain`은 프레임워크 애노테이션을 갖지 않는다. 시간은 `Clock` 주입, 난수 금지.
- 모든 판정 로직은 "입력 → 결과" 순수 함수 시그니처. 예: `AlertDecision decide(DealEvent, AlertPolicy, BenchmarkView)`.
- 수치 파라미터는 하드코딩 금지 — `BenchmarkParams`(seam) 주입만 참조([`../docs/31-detailed-params.md`](../docs/31-detailed-params.md)).
- `adapter`는 얇게. 로직이 스며들면 리팩토링으로 `domain`에 밀어 넣는다.

## DB
- Flyway 마이그레이션은 **core 단독 소유**(`src/main/resources/db/migration`). collector는 계약 테이블만 접근.
- 통합 테스트는 Testcontainers PostgreSQL 16. 순수 도메인은 DB 없이 단위 테스트.
