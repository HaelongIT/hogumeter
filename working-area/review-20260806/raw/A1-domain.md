# A1 — core 순수 도메인 리뷰
## 요약 (High 1 / Medium 1 / Low 0 / Info 2)
`domain/` 76파일 전부를 우선순위대로(benchmark→alert→deal→matching→purchase→used→나머지) 읽었다. 산식·상태기계·정직성 필터(`DealSets`)는 전반적으로 견고하고, 상태 전이는 프로덕션 경로에서 실제로 `canTransitionTo`/`transitionTo`로 검증되고 있음을 호출자 추적으로 확인했다(처음엔 `DealEvent.activate()/verify()/end()`가 호출자 0으로 보여 의심했으나, `DealMergePolicy`가 직접 계산한 상태를 `DealEventEntity.applyMerge`/`applyStatusChange`가 엔티티 계층에서 재검증하는 구조였다 — 오탐 기각). 실제 결함은 별칭 매칭의 미정의 순서 의존(별칭이 여럿 겹칠 때 임의 선택)과 성적 없는 관찰 문맥 계산의 0-나눗셈 두 건이다.

### A1-01 — 별칭 사전 substring 다중 히트 시 매칭 결과가 JVM 실행마다 달라질 수 있다 · High
- **위치**: `core/src/main/java/dev/hogumeter/core/domain/matching/AliasDictionary.java:22-27`
- **근거**:
  ```java
  public Optional<Long> match(String joinedTitle) {
      return aliases.entrySet().stream()
              .filter(e -> joinedTitle.contains(e.getKey()))
              .map(Map.Entry::getValue)
              .findFirst();
  }
  ```
  `aliases`는 컴팩트 생성자에서 `Map.copyOf(aliases)`로 감싸진다(`AliasDictionary.java:13-15`). `Map.copyOf`/`Map.of`류 불변 컬렉션은 Javadoc이 "반복 순서는 unspecified"라고 명시하며, JDK 구현은 이를 JVM 실행마다 무작위화한다(해시 플러딩 방지용 salt). 호출부 `CatalogProjection.aliasDictionary()`(`core/src/main/java/dev/hogumeter/core/adapter/persistence/CatalogProjection.java:65-73`)도 `HashMap`을 만들어 넘기므로 삽입 순서 자체도 보존되지 않는다. 즉 두 개 이상의 별칭이 같은 제목에 substring으로 동시에 걸리면(예: "아이폰17"이 "아이폰17프로" 별칭의 부분 문자열인 경우처럼 계열 제품명이 흔히 그렇다) `findFirst()`가 반환하는 productId는 순전히 불특정 반복 순서에 달려 있다 — **어느 제품이 이길지 코드 어디에도 규칙이 없다.**

  같은 패키지 안에 정확히 이 상황을 다루는 대조군이 있다: `DemandAxisSpec.valueIn`(`core/src/main/java/dev/hogumeter/core/domain/matching/DemandAxisSpec.java:41-52`)은 후보가 둘 이상 걸리면 명시적으로 `null`(미상)을 반환해 "모르는 것을 아는 척하지 않는다"를 코드로 강제한다. `AliasDictionary.match`는 같은 종류의 모호성을 감지조차 하지 않고 조용히 하나를 골라 **CONFIRMED**로 확정한다 — 이 결과는 미상 큐로도 가지 않는다(`Matcher.match`, `AliasDictionary.java` 사용부: `core/src/main/java/dev/hogumeter/core/domain/matching/Matcher.java:20-24`).
- **실패 시나리오**: 카탈로그에 제품 A(별칭 "아이폰17")와 제품 B(별칭 "아이폰17프로")가 등록돼 있다고 하자. "아이폰 17 프로 256기가 팝니다" 원문의 정규화·공백제거 제목 "아이폰17프로256기가"는 두 별칭 모두를 substring으로 포함한다. `core` 컨테이너를 재기동하면(배포·롤백·OOM 재시작 등 이 프로젝트에서 드물지 않은 이벤트) `aliases` 맵의 반복 순서가 바뀌어, 어제는 B(프로)로 CONFIRMED되던 같은 표현의 딜이 오늘은 A(일반)로 CONFIRMED될 수 있다. 사람 확인 없이 바로 딜이 생성/병합되므로(리뷰 큐를 거치지 않음) 잘못된 제품의 기준가 분포에 실가격이 섞여 median·P25가 오염된다 — "정직성" 원칙 위반이자 잘못된 값 표시.
- **이미 막는 장치 확인**: `core/src/test/java/dev/hogumeter/core/domain/matching/MatcherTest.java` 전체를 확인했다 — 별칭이 하나만 등록된 시나리오만 다루고, 별칭 두 개가 같은 제목에 동시 히트하는 케이스는 없다. `AliasDictionary`만 단독으로 테스트하는 파일도 없다(`find core/src/test -iname "*AliasDict*"` 결과 없음). CI 게이트에도 이 순서 의존성을 잡는 장치가 없다.
- **권고**: `AliasDictionary.match`도 `DemandAxisSpec.valueIn`과 같은 패턴으로 바꾼다 — 매칭되는 별칭이 둘 이상이면(서로 다른 productId를 가리키면) 그 자리에서 확정하지 말고 미상/후보로 내려보낸다. 최소 변경으로는 "가장 긴 별칭 우선"(더 구체적인 표현이 이긴다) 규칙을 명시하거나, 둘 이상 히트 시 `Optional.empty()`를 반환해 `Matcher`가 CANDIDATE/UNKNOWN 경로로 보내게 한다.

### A1-02 — `paidPrice=0`인 구매의 관찰 문맥 계산이 0으로 나누기로 예외를 던진다 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/domain/purchase/ObservationContextCalculator.java:24-27`
- **근거**:
  ```java
  long overpaid = purchase.paidPrice() - lowest;
  BigDecimal pct = BigDecimal.valueOf(overpaid)
          .divide(BigDecimal.valueOf(purchase.paidPrice()), 3, RoundingMode.HALF_UP);
  ```
  `purchase.paidPrice()`가 0이면 `BigDecimal.divide`가 `ArithmeticException: Division by zero`를 던진다. `Purchase` 레코드(`core/src/main/java/dev/hogumeter/core/domain/purchase/Purchase.java:15-16`)에는 `paidPrice`에 대한 컴팩트 생성자 검증이 전혀 없고, 이 값을 채우는 `RecordPurchaseCommand`(`core/src/main/java/dev/hogumeter/core/application/RecordPurchaseCommand.java:13-20`)도 검증이 없으며, `PurchaseController.record`(`core/src/main/java/dev/hogumeter/core/adapter/web/PurchaseController.java:29-31`)는 `@Valid` 없이 그대로 `@RequestBody`를 유스케이스에 넘긴다. 이 도메인에는 `DealTags.FREE_PRICE`(0원 무료 배포 딜)가 이미 1급 개념으로 존재해(`core/src/main/java/dev/hogumeter/core/domain/deal/DealTags.java:29-34`) "0원에 샀다"는 구매 기록도 현실적으로 발생할 수 있는 입력이다.
- **실패 시나리오**: 사용자가 `POST /api/v1/purchases`로 `paidPrice: 0`(무료로 받은 이벤트 사은품 등)을 기록한다 → `OBSERVING` 상태로 저장됨(막는 검증 없음) → 이후 같은 variant에 활성 딜이 하나라도 생기면(`GetPurchaseObservationsUseCase` → `ObservationContextCalculator.compute`) `signalSet`이 비어있지 않아 `activeDeal` 분기를 타고 `purchase.paidPrice()`(=0)로 나누다가 `ArithmeticException`이 던져진다 — 그 사용자의 구매 관찰 목록 조회 API 전체가 500으로 죽는다(판단 화면이 그 구매를 아예 못 보여준다).
- **이미 막는 장치 확인**: `core/src/test/java/dev/hogumeter/core/domain/purchase/ObservationContextCalculatorTest.java` 전체 확인 — `observing(paidPrice)` 헬퍼가 항상 900_000/950_000/900_000/850_000 등 양수만 쓰고 0 케이스가 없다. `Purchase`·`RecordPurchaseCommand`·`RecordPurchaseUseCase`·`PurchaseController`를 모두 확인했지만 어디에도 `paidPrice > 0` 검증이 없다. DB 쪽도 `V2__purchase.sql`에 `paid_price bigint not null`만 있고 CHECK 제약은 없다.
- **권고**: `Purchase` 컴팩트 생성자(또는 `RecordPurchaseCommand`)에 `paidPrice >= 0`을 요구하는 도메인 검증을 추가하거나(0을 허용하려면), `ObservationContextCalculator.compute`에서 `purchase.paidPrice() == 0`일 때 퍼센트를 `null`로 두고 원화 상회분만 내도록 분기한다(`BenchmarkCalculator.leg`가 기준가 0/참조 부재를 다루는 패턴과 동일하게 "지어내지 않는다"를 적용).

## 검토했으나 문제없음 (근거)
- `BenchmarkCalculator`(BM-06 산식 전체): 분위수 계산, K_FILL 자동확장(표본 실제 증가 시에만 갱신), 갭 계산의 null 가드, `qualifiesAsColdStartJackpot`의 currentPrice null 처리 모두 docs/benchmark/04와 일치. `Quantiles.percentile`은 n=1·경계(h=n-1) 모두 인덱스 초과 없이 처리.
- `AlertEvaluator`/`AlertIntensity`/`AlertGate`/`QuietHours`: 강도 서열이 선언 순서와 문서가 일치, ENDED 딜 억제(Q-27③) 확인, quiet hours의 Asia/Seoul 시간대 문제는 이미 커밋 `2baf21a`로 수정되어 `CoreApplication.clock()`이 `ZoneId.of("Asia/Seoul")`을 명시(`CoreApplicationTests`가 회귀 테스트로 고정).
- `DealEvent`/`DealStatus`/`DealMergePolicy`/`DealEventEntity`: 처음엔 `activate()/verify()/end()`가 프로덕션 호출자 0으로 보여 "상태기계 우회" 결함을 의심했으나, `DealMergePolicy.merge`가 계산한 상태를 `DealEventEntity.applyMerge`가 `canTransitionTo`로, `ReprocessDealStatusUseCase`가 `applyStatusChange`(내부에서 `transitionTo`)로 실제 검증하고 있어 안전망이 살아있음을 확인(주석에 "Q-84 상태 전이 안전망"으로 명시).
- `DealSets`(pricingSet/occurrenceSet/signalSet): 배송비미상·무료가·이상치·영구제외 필터가 문서(Q-46②, D-5)와 정확히 일치.
- `Purchase`/`PurchaseState`/`PurchaseTriggers`/`ReportCardCalculator`: 만료 전이가 `ExpirePurchaseObservationsUseCase`에서 실제로 호출됨을 확인(스케줄러 부재로 죽은 전이였던 과거 교훈이 이미 반영됨). `ReportCardCalculator`는 n=0을 나눗셈 전에 분기해 0-나눗셈 없음.
- `used/*`(20파일): `PriceContextCalculator`는 `benchmarkPrice<=0`을 사전에 걸러 0-나눗셈 없음. `ListingDiff`는 `LinkedHashMap`으로 결정적 dedupe. `UsedMatcher`/`UsedAlertPolicy`/`UsedRiskSignals` 로직이 문서(docs/used/04 AC)와 일치.
- 도메인 전체 `Instant.now()`/`LocalDate.now()`/`System.currentTimeMillis()`/`new Date()`/`ZoneId.systemDefault()` grep — 0건. `Clock` 주입 규약 전수 준수.
- 컬렉션 방어적 복사: `DealEvent`, `UsedSearchSpec`, `BonusGroup`, `PriceContext`, `AlertDecision`, `MatchResult`, `ProductMatchSpec`, `VariantSpec`, `DemandAxisSpec`, `ReviewQueueItem` 등 record 컴팩트 생성자에서 `List.copyOf`/`Set.copyOf`/`Map.copyOf` 확인.

## 시간·범위 한계로 못 본 것
- `domain/product/`(AxisType·DemandAxisMode)는 단순 열거형이라 통상 리뷰에서 제외했다 — 별도 로직 없음을 짧게 훑기만 함.
- `RuleBasedListingExtractor`의 "본문 내 최댓값 = 매물가" 휴리스틱이 "정가 1,200,000원 / 판매가 800,000원"처럼 더 큰 금액이 실제 판매가가 아닌 경우 오독할 수 있음을 발견했으나, 코드 주석이 이 트레이드오프를 이미 인지하고 폴백 경로(AC-12③ 수동 입력)로 완화한다고 명시해 "이미 받아들인 v1 한계"로 보고 별도 결함으로 올리지 않았다. web에서 이 값을 어떻게 검증받는지는(A1 범위 밖) 확인하지 못했다.
- Testcontainers 기반 통합 테스트(`core/src/test/.../adapter/**`)는 시간 관계상 실행하지 않았다 — 두 발견 모두 정적 코드 대조와 순수 도메인 단위테스트 부재 확인으로만 검증했다(실행 검증은 못 함).
