# A2 — core 애플리케이션 계층 리뷰

## 요약 (High 1 / Medium 3 / Low 1 / Info 0)
`application/` 84파일 전수를 읽었다(51 UseCase 전부 + 협력자 18개 + port/out). 전반적으로 트랜잭션 경계·sentinel 회피·정직성 원칙이 매우 꼼꼼히 지켜져 있고(특히 벌크 UPDATE 후 `entityManager.refresh()` 패턴, "값 없음=null" 관례가 전 파일에 일관됨), 의도적 트레이드오프(N+1, 1인용 규모)는 대부분 주석으로 근거를 남겨 뒀다. 다만 딜 병합 경로에서 **도메인이 계산한 수요축 값이 엔티티에 반영되지 않는 결함**을 하나 확인했다 — SPLIT 제품의 표본 오염 방지라는 이 프로젝트의 핵심 설계 의도를 직접 무력화하는 값 손실이라 High로 분류한다. 나머지는 트랜잭션 경계·N+1 관련 Medium 3건.

### A2-01 — 딜 병합 시 도메인이 계산한 수요축 값이 엔티티에 반영되지 않는다 · High
- **위치**: `core/src/main/java/dev/hogumeter/core/application/IngestDealsUseCase.java:154-156`
- **근거**:
  ```java
  DealEvent merged = mergePolicy.merge(existingDomain, candidate);
  existing.applyMerge(merged.priceFirst(), merged.priceMin(), merged.priceMax(), merged.priceLast(),
          merged.crossVerified(), merged.status(), merged.firstSeen(), merged.lastSeen());
  ```
  `DealMergePolicy.merge()`(`core/src/main/java/dev/hogumeter/core/domain/deal/DealMergePolicy.java:69,78-86`)는 두 딜의 수요축 값을 신중하게 합성한다 — 한쪽만 알면 아는 값, 서로 다르면 **의도적으로 null(미상)**로 되돌린다("블랙 글과 화이트 글을 한 딜로 합쳤다면 어느 분포에 넣을지 알 수 없다 … 하나를 골라 담으면 그 분포가 조용히 오염된다"). 그런데 이 계산 결과 `merged.demandAxisValue()`는 위 `applyMerge` 호출에 **인자로 전달되지 않는다**. `DealEventEntity.applyMerge`(`core/src/main/java/dev/hogumeter/core/adapter/persistence/DealEventEntity.java:195-208`)는 애초에 `demandAxisValue` 파라미터 자체가 없고, 엔티티의 `demandAxisValue` 필드는 생성자(`:101,116`)에서만 설정되며 그 외에 이를 바꾸는 setter/메서드가 없다. 즉 병합이 일어나면 도메인이 계산한(때로는 null로 정정한, 때로는 새로 채운) 값은 버려지고 **최초 생성 시점의 옛 값이 영구히 유지**된다.
- **실패 시나리오**:
  1. (조용한 표본 오염) SPLIT 제품 variant(예: "갤럭시25 256GB")에 "갤럭시 25 256기가 블랙 특가 890,000"이 먼저 들어와 `demandAxisValue="블랙"`인 딜이 생성된다. 잠시 후 다른 사이트에서 "갤럭시 25 256기가 화이트 890,000"(가격 유사, 병합 시간창 이내)이 들어온다. `DealMergePolicy.sameTarget`(같은 variantId만 확인, 수요축 값은 보지 않음)과 `canMerge`가 이 둘을 같은 딜로 병합 대상 판정한다. `mergeDemandAxisValue("블랙","화이트")`는 `null`(미상)을 계산하지만, 엔티티는 여전히 `demandAxisValue="블랙"`으로 남는다 — 실제로는 블랙·화이트가 섞인 딜이 계속 "블랙" 분포의 표본으로 기준가·신호등에 들어간다. 이 프로젝트가 반복적으로 경계해 온 바로 그 오염("블랙 딜을 화이트가 섞인 기준가에 대는 셈", `VariantDemandScope` javadoc 등)이 병합 경로에서만 실제로 뚫려 있다.
  2. (조용한 유실 상태 고착) 반대 방향도 있다: 첫 글이 색을 못 밝혀 `demandAxisValue=null`로 생성되고 `enqueueIfDemandUnknown`이 DEMAND_UNKNOWN 큐에 올린다(`IngestDealsUseCase.java:185,236-244`). 이후 같은 딜에 병합되는 다른 사이트 글이 제목에 색을 명시해 `mergeDemandAxisValue(null,"블랙")="블랙"`을 계산해도, 엔티티는 계속 `null`로 남는다 — 자동으로 풀렸어야 할 미상 상태가 사람이 큐에서 수동 처리하기 전까지 영원히 그대로다.
- **이미 막는 장치 확인**: `IngestDealsUseCaseTest`에 `demandAxisValueIsParsedFromTheTitleAndReachesTheDeal`·`demandAxisValueIsUnknownWhenTheTitleDoesNotSayIt`·`splitDealWithUnknownValueGoesToTheReviewQueue`가 있지만 전부 **병합이 일어나지 않는(딜 1건짜리) 케이스**만 검증한다. `secondSiteMergesIntoVerifiedDeal`(:163) 등 병합 테스트들은 가격·상태·후속 알림만 확인하고 `demandAxisValue`는 단정하지 않는다. `DealMergePolicyTest`(도메인)는 `mergeDemandAxisValue`를 순수 함수로는 GREEN이겠지만, 그 값이 실제로 엔티티까지 관통하는지 보는 테스트는 없다 — "부품별 GREEN은 계약을 보장하지 않는다"의 전형.
- **권고**: `DealEventEntity.applyMerge`에 `demandAxisValue` 파라미터를 추가하고 `IngestDealsUseCase.confirmDeal`에서 `merged.demandAxisValue()`를 넘긴다. 병합으로 값이 새로 null이 되는 경우 그 딜을 `enqueueIfDemandUnknown`과 동일하게 DEMAND_UNKNOWN 큐에 올릴지도 함께 검토(현재는 신규 생성 경로에만 그 큐잉이 있다).

### A2-02 — 미상 큐 승격의 부수효과가 원자적 상태 가드보다 먼저 실행된다 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/application/ResolveReviewItemUseCase.java:102-114, 199-207`
- **근거**:
  ```java
  public void promote(long reviewItemId, Long variantId, String channel) {
      Item item = readPending(reviewItemId);          // 1) PENDING 확인(비원자적 읽기)
      if (UNCLASSIFIED.equals(item.type())) {
          promoteUnclassified(reviewItemId, item, variantId);   // 2) 딜 생성 + 별칭 학습(부수효과)
          resolve(reviewItemId, "CONFIRMED", channel);           // 3) 원자적 상태 전이(가드는 여기뿐)
          return;
      }
      ...
  }
  private void resolve(long reviewItemId, String status, String channel) {
      int updated = jdbc.update(
              "update review_queue_item set status = ?, channel = ?, resolved_at = now() "
                      + "where id = ? and status = 'PENDING'", ...);
      if (updated == 0) {
          throw new ReviewItemNotFoundException(reviewItemId);
      }
  }
  ```
  클래스 javadoc은 "처리는 PENDING 행에만 원자적으로 건다({@code where status='PENDING'})"고 명시하지만, 실제 원자적 가드는 `resolve()`의 `UPDATE ... WHERE status='PENDING'` 한 문장뿐이다. 그 앞의 `readPending`(단순 SELECT)과 `promoteUnclassified`(딜 생성 `ingestDeals.confirmDeal` 호출 + `learnAlias` 별칭 저장)는 이 가드 이전에 실행된다.
- **실패 시나리오**: 같은 `reviewItemId`에 대해 텔레그램 인라인 버튼이 짧은 시간 내 두 번 눌리면(전송 지연으로 인한 중복 탭, 또는 텔레그램의 콜백 재전달) 두 요청이 거의 동시에 `readPending`을 통과해 둘 다 `promoteUnclassified`를 실행할 수 있다. 그 결과 `ingestDeals.confirmDeal`이 두 번 호출되어(같은 variantId·같은 post) `DealMergePolicy.canMerge` 판정에 걸리지 않는 시점 차이가 생기면 중복 딜이 생성될 수 있고, `learnAlias`도 두 번 시도된다(다만 `alias_dictionary`의 `unique(product_id, alias)` 덕에 별칭 자체는 안전). 최종적으로 `resolve()`는 한쪽만 성공하고(두 번째 호출은 `ReviewItemNotFoundException`으로 404), 사용자는 "이미 처리됨" 응답을 받지만 이미 실행된 `confirmDeal`의 부수효과는 되돌려지지 않는다.
- **이미 막는 장치 확인**: `ResolveReviewItemUseCaseTest.resolvingMissingOrAlreadyResolvedItemThrows`(:196)는 **순차** 이중 처리(첫 호출 성공 후 두 번째 호출)만 검증한다. 동시성(레이스) 시나리오를 재현하는 테스트는 없다.
- **권고**: `readPending`부터 `resolve`까지를 하나의 원자적 조건부 업데이트로 묶거나(예: 먼저 `UPDATE ... WHERE status='PENDING'`으로 행을 선점한 뒤에만 부수효과를 실행), 최소한 `promoteUnclassified` 진입 전에 낙관적 잠금/재확인을 추가한다.

### A2-03 — SendDigestUseCase 발송 후 저장물 갱신 루프에 트랜잭션 경계가 없다 · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/application/SendDigestUseCase.java:79-97`
- **근거**:
  ```java
  public DigestSendReport send() {
      ...
      boolean allSucceeded = sent == parts.size();
      if (allSucceeded) {
          for (VariantDigestRow row : digest.variantRows()) {
              recordSent.recordSent(row.variantId(), row.transition().to().name(), contextFor(row.variantId()),
                      basisModeFor(row.variantId()));
          }
          incrementQueueAppearances.increment(digest.queue().stream().map(PendingItem::id).toList());
      }
      return new DigestSendReport(parts.size(), sent, allSucceeded);
  }
  ```
  `send()` 자체는 `@Transactional`이 아니고, `RecordDigestSentUseCase.recordSent`(`RecordDigestSentUseCase.java:32-34`)도 `@Transactional`이 없다 — `digestStates.save(...)` 한 줄이 Spring Data 기본 동작으로 자기 트랜잭션에서 개별 커밋된다. `IncrementDigestQueueAppearancesUseCase.increment`는 자체 `@Transactional`(별개 트랜잭션)이다. 즉 N개 variant에 대한 `recordSent` 루프와 뒤이은 큐 증분은 **하나의 원자적 단위가 아니다**.
- **실패 시나리오**: 텔레그램 발송이 전부 성공한 뒤(`allSucceeded=true`), `recordSent` 루프 도중 3번째 variant에서 DB 예외(락 충돌·일시적 연결 단절 등)가 발생하면 1·2번째 variant의 `digest_state`는 이미 개별 커밋돼 있고, 3번째 이후 variant들과 리뷰큐 `digest_appearances` 증분은 전혀 반영되지 않은 채 예외가 위로 전파된다. 다음 주 다이제스트 실행 시 `ComputeDigestTransitionUseCase`가 variant마다 서로 다른 기준 시점(어떤 variant는 이번 주 색을 이미 저장, 어떤 variant는 저장 안 됨)으로 전환을 판정하게 되어 "실제로는 보낸 다이제스트인데 일부 variant만 다음 주 전환 비교의 베이스라인이 갱신"되는 불일치가 생긴다. 클래스 javadoc이 명시한 "전 분할 성공 시에만 저장물을 갱신한다(REL-03 원자성)" 계약은 텔레그램 발송 성공 여부(`allSucceeded`)만 게이트할 뿐, 그 뒤의 DB 쓰기 자체의 원자성은 보장하지 않는다.
- **이미 막는 장치 확인**: `SendDigestUseCaseTest`(:75-152)는 `allSucceeded`가 false일 때 저장물이 전혀 갱신되지 않는지는 검증하지만(`failedSendDoesNotRecordDigestState`·`failedSendDoesNotIncrementDigestAppearances`), **발송은 전부 성공했는데 DB 쓰기 도중 일부만 실패**하는 시나리오(부분 커밋)는 테스트되지 않는다.
- **권고**: `send()`(또는 `recordSent`+`incrementQueueAppearances` 호출부)를 `@Transactional`로 감싸 N개 variant 갱신 + 큐 증분을 하나의 트랜잭션으로 묶는다.

### A2-04 — ReprocessDealPricesUseCase가 원문 조회에서 N+1을 낸다(형제 클래스는 배치 조회를 쓴다) · Medium
- **위치**: `core/src/main/java/dev/hogumeter/core/application/ReprocessDealPricesUseCase.java:108-117`
- **근거**:
  ```java
  private List<PriceEvidence> evidenceFor(DealEventEntity deal) {
      return sources.findByDealEventId(deal.getId()).stream()
              .map(DealEventSourceEntity::getRawDealPostId)
              .map(rawPosts::findById)
              .flatMap(Optional::stream)
              .filter(post -> post.getHeadlinePrice() != null)
              .map(ReprocessDealPricesUseCase::toEvidence)
              .toList();
  }
  ```
  `reprocessPriceChanges()`(:59-68)는 `dealEvents.findByStatusIn(REFRESHABLE)`로 얻은 **ACTIVE·VERIFIED 전 딜**마다 `refresh(deal)`→`evidenceFor(deal)`을 부르고, 그 안에서 소스 하나당 `rawPosts.findById`를 **개별 호출**한다. 같은 파일에서 파생된 `previewPinnedPriceIncreases()`(:79-87)도 핀마다 같은 패턴을 반복한다. 이 파이프라인 단계는 매 틱(스케줄러) 전체 활성 딜을 대상으로 돌기 때문에, 딜 수 × 평균 소스 수만큼의 개별 쿼리가 발생한다. 반면 바로 옆의 `ReprocessDealStatusUseCase.endIfAllSourcesEnded`(`ReprocessDealStatusUseCase.java:56-62`)는 같은 문제를 `rawPosts.findAllById(rawIds)`로 **배치 조회**해 정확히 이 패턴을 피하고 있어, 이 파일만 일관성이 깨진 상태다.
- **실패 시나리오**: 딜 수가 늘어날수록(예: 활성 딜 200건 × 평균 소스 2건) 매 60초 틱마다 400회 이상의 개별 `SELECT ... WHERE id = ?`가 발생한다. `GetPrioritizedProductsUseCase`·`ExpirePurchaseObservationsUseCase` 등 다른 파일들은 "1인용 규모라 N+1을 그대로 둔다(PERF-04)"는 근거를 명시적으로 남겨 이 트레이드오프를 승인된 결정으로 표시하는데, 이 파일에는 그런 근거가 없고 형제 클래스(`ReprocessDealStatusUseCase`)는 이미 배치 조회로 회피하고 있어 의도된 결정이라기보다 누락으로 보인다.
- **이미 막는 장치 확인**: `ReprocessDealPricesUseCaseTest`(존재 확인함)는 기능(가격 갱신 여부)만 검증하고 쿼리 횟수를 재는 테스트는 없다.
- **권고**: `evidenceFor`를 `sources.findByDealEventId` 결과에서 `rawDealPostId` 목록을 모은 뒤 `rawPosts.findAllById(ids)` 한 번으로 바꾼다(이미 `ReprocessDealStatusUseCase`에 같은 패턴이 있어 이식 비용이 낮다).

## 검토했으나 문제없음 (근거)
- **`IngestDealsUseCase.candidateFrom`의 `dealEventId=0L` 자리표시자**(:255-257): 병합 경로는 `DealMergePolicy.merge`가 항상 `existing.dealEventId()`를 취하고(`DealMergePolicy.java:70`), 신규 경로는 `dealEvents.save(...)` 이후 `mapper.toDomain(created)`로 실제 DB id를 다시 채운 값만 `alertEvaluation.evaluate`에 전달한다(`IngestDealsUseCase.java:186`). 0L이 실제로 읽히는 경로가 없음을 코드로 확인했다.
- **`ExpirePurchaseObservationsUseCase`·`IssuePendingReportCardsUseCase`·`ArchivePurchaseUseCase`·`AlertPolicySettingsUseCase`의 벌크 UPDATE 패턴**: 전부 `entityManager.refresh(entity)`로 영속성 컨텍스트 우회를 스스로 정정하고 있어(`core-java` 규칙 그대로 준수), 트랜잭션 내 재조회 시 옛 값이 나올 위험이 없다.
- **`FlushHeldAlertsUseCase`·`FollowUpAlertUseCase`의 멱등성**: `(deal_event_id, kind)` 존재 확인 후에만 저장하는 패턴이 일관돼 있어 재실행 시 중복 발송 위험이 낮다. `FlushHeldAlertsUseCase`는 처리한 보류 건을 발송·드롭 무관하게 큐에서 지우고 재보류는 `evaluate`가 다시 넣는 구조로 유실 없이 순환한다.
- **`GetPrioritizedProductsUseCase`·`GetWatchItemsUseCase`·`GetComparisonUseCase.toRow`의 N+1**: `GetPrioritizedProductsUseCase`는 "1인용 규모라 N+1을 그대로 둔다(PERF-04) — GetReviewQueueUseCase와 같은 판단"이라고 명시적으로 근거를 남겼고, 나머지 둘도 규모상(활성 핀·비교 매물 수는 수십 건 이내) 실질적 위험이 낮아 A2-04와 달리 별도 결함으로 올리지 않았다.
- **`VariantExcludeKeywords.hitsAnyKeyword`의 N+1(딜당 소스 조회 + 소스당 원문 조회)**: `ReprocessDealPricesUseCase`(A2-04)와 같은 패턴이지만, 이 경로는 제외 키워드가 **설정된 variant에서만** 실행되고(`filterCounting`이 키워드 없으면 즉시 원본을 반환), 매 틱 전체 딜을 도는 것이 아니라 조회·알림 판정 시점에만 호출돼 빈도가 훨씬 낮다 — 별도 항목으로 올리기보다 A2-04에 준하는 낮은 우선순위로 남긴다.
- **`ReviewCallbackRouter`의 SEC-03 화이트리스트**: `allowedChats`가 비면 아무도 허용하지 않는 닫힌 기본값이고, 예외 처리도 폴러를 죽이지 않도록 전부 `CallbackResult`로 흡수한다 — 의도대로 동작.
- **`RegisterProductUseCase`·`RegisterUsedSearchUseCase`의 경계 검증**: `validate(cmd)`가 null 체크로 curl 우회를 막고 있어(Q-49), REST에서 온 값이 검증 없이 그대로 DB로 흐르지 않는다.

## 시간·범위 한계로 못 본 것
- 예외 클래스 15개(`*Exception.java`)는 필드·메시지 수준만 훑고 정적 분석은 생략했다(로직이 거의 없어 결함 가능성이 낮다고 판단).
- `port/out/*` 인터페이스 8개는 시그니처만 확인했고, 구현체(어댑터)는 이 리뷰 범위(A2)가 아니라 보지 않았다.
- `DefineComparisonAxesUseCase.ensure`(추가 전용, `@Transactional` 없음)의 체크-후-저장 루프에 동시성 레이스가 있을 수 있으나, USED-05 비교축은 사용 빈도가 낮고 영향이 제한적이라 심층 분석은 생략했다.
- 도메인 계층(`domain/**`)의 순수 로직 자체(예: `DealMergePolicy.priceWithinTolerance`의 반올림 규칙, `BenchmarkCalculator` 산식)는 A2 범위 밖이라 검증하지 않았다 — A2-01은 애플리케이션 계층의 배선 결함만 지적한다.
- 51개 UseCase 전부를 읽었으나 트랜잭션·N+1·sentinel·예외삼킴 패턴 위주로 훑었고, 각 파일의 모든 분기·경계값을 뮤테이션 수준으로 검증하지는 못했다.
