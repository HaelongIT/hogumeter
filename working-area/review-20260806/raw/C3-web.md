# C3 — web (React) 리뷰

## 요약 (High 3 / Medium 1 / Low 0 / Info 2)
`web/src/**` 53파일을 정적으로 훑었다. 핵심 결함은 **응답 레이스와 상태 초기화 누락** 두 갈래다 — `DecisionPage`가 SPLIT variant로 전환하면서 아직 축 값을 고르지 않았을 때 이전 variant의 판단 요약(기준가·신호등·갭)을 지우지 않고 그대로 남기고, `PurchasePanel`·`UsedComparisonPage`는 variant/product 전환 시 `AlertPolicyPanel`·`WatchPage`·`UsedSearchPage`가 이미 쓰고 있는 `live` 가드 패턴이 빠져 있어 응답이 늦게 도착하면 다른 대상의 값이 화면에 남을 수 있다. `types.ts` ↔ core 전수 대조는 실질적인 필드 불일치는 없었고, 다이제스트용 `digestAppearances` 필드 누락과 `ApiError` 코드 카탈로그 완전성만 Info로 남긴다.

### C3-01 — variant를 GROUPED→SPLIT(축 미선택)으로 바꾸면 이전 variant의 판단 요약이 안 지워진다 · High
- **위치**: `web/src/decision/DecisionPage.tsx:107-124`
- **근거**:
  ```tsx
  useEffect(() => {
    setDemandAxisValue(null)
  }, [variantId])

  useEffect(() => {
    if (variantId === null) return
    if (demandAxis !== null && demandAxisValue === null) return // 색을 고르기 전엔 묻지 않는다
    let live = true
    setError(null)
    setLoaded(null)
    ...
  }, [variantId, periodMonths, demandAxis, demandAxisValue, includeOutliers])
  ```
  두 `useEffect`는 같은 커밋에서 선언 순서대로 실행된다. `variantId`가 바뀔 때 첫 번째 effect가 `setDemandAxisValue(null)`을 호출하지만, **이 값이 호출 시점에 이미 `null`이면 React가 동일 값 갱신을 bail-out**해 재렌더를 트리거하지 않는다(React의 표준 동작). 그 결과 두 번째 effect(본 조회 effect)는 **같은 렌더에서 여전히 옛 `demandAxisValue`(=`null`)**를 보고 실행되는데, 새 variant가 SPLIT이면 `demandAxis !== null && demandAxisValue === null` 조건이 참이 되어 **`return`으로 조기 종료한다 — 이 경로엔 `setLoaded(null)`·`setError(null)`가 없다.**
  즉 "GROUPED variant(축 없음, `demandAxisValue`가 계속 `null`이던 상태)에서 데이터를 이미 띄운 뒤, SPLIT variant로 전환"하면 새 조회가 발화하지 않을 뿐 아니라 **이전 variant의 `loaded`(신호등·기준가·갭·주기)가 화면에 그대로 남는다.**
- **실패 시나리오**: 사용자가 아이폰(GROUPED) variant를 선택해 "핫딜 기준가 820,000원 · 12건(교차 3건)" 판단 요약을 본다. 이어서 갤럭시(SPLIT) variant로 셀렉트를 바꾼다. 화면 위쪽엔 "색상을 고르세요"라는 축 선택 안내가 새로 뜨지만, 그 아래 `판단 요약` 섹션(`aria-label="판단 요약"`)은 **아이폰의 신호등·기준가·갭이 그대로 남아** 마치 갤럭시의 판단인 것처럼 보인다. 사용자가 축 값을 고를 때까지(수 초~수십 초, 혹은 안 고르면 영구히) 다른 제품의 값을 보고 판단하게 된다.
- **이미 막는 장치 확인**: `web/src/decision/DecisionPage.test.tsx`를 확인했다. `describe('DecisionPage — 수요축 분리 제품', …)` 블록은 galaxy 하나만 `beforeEach`로 독립 렌더하며, GROUPED variant를 먼저 로드한 뒤 같은 렌더 트리에서 SPLIT variant로 전환하는 테스트는 없다. `pick()` 헬퍼도 단일 선택만 수행한다. 가드 없음.
- **권고**: 두 effect를 하나로 합치거나, `demandAxisValue` 리셋 effect에서 `setDemandAxisValue`뿐 아니라 `setLoaded(null)`·`setError(null)`도 함께 호출한다. 혹은 조회 effect의 조기 return 두 곳(`variantId === null`, `demandAxis !== null && demandAxisValue === null`) 모두에서 `setLoaded(null)`을 먼저 실행하도록 만든다(React state bail-out에 의존하지 않는 형태로).

### C3-02 — PurchasePanel이 variant 전환 응답 레이스를 막지 않는다 · High
- **위치**: `web/src/purchase/PurchasePanel.tsx:74-78`
- **근거**:
  ```tsx
  useEffect(() => {
    setPurchases(null)
    setError(null)
    void reload(variantId)
  }, [variantId])
  ```
  `reload`는 `api.listPurchases(id).then(setPurchases)`일 뿐이고, 이 effect엔 `AbortController`나 `let live = true` 같은 무시 플래그가 없다(같은 파일의 `AlertPolicyPanel.tsx:79-98`·`WatchPage.tsx:45-66`·`UsedSearchPage.tsx:39-52`는 전부 이 패턴을 쓴다 — 이 파일만 빠졌다). `variantId`가 짧은 시간에 두 번 바뀌면(예: 판단 화면에서 variant 셀렉트를 빠르게 두 번 조작) 두 `listPurchases` 요청이 동시에 in-flight 상태가 되고, **먼저 보낸 요청(옛 variantId)의 응답이 나중에 도착하면** 그 결과가 `setPurchases`로 마지막에 덮어써 **현재 선택된 variant와 다른 variant의 구매 기록**이 화면에 고정된다.
- **실패 시나리오**: 사용자가 variant A → variant B로 빠르게 전환한다(예: 드롭다운 두 번 연속 클릭). 네트워크 지연으로 A의 `listPurchases` 응답이 B의 응답보다 늦게 도착하면, 최종적으로 화면의 "구매 목록"엔 **B를 보고 있는데 A의 구매 기록**이 남는다. 사후 "호구였나"를 판단하는 성적표·관찰 문맥이 엉뚱한 variant 것으로 보인다.
- **이미 막는 장치 확인**: `web/src/purchase/PurchasePanel.test.tsx:124-130` `'variant가 바뀌면 그 variant의 기록을 다시 부른다'` 테스트를 확인했다 — `rerender`로 11→12를 바꾸되 **각 단계마다 `waitFor`로 먼저 완료시킨 뒤** 다음으로 넘어가는 순차 테스트라, 응답이 out-of-order로 도착하는 경우를 재현하지 않는다. 가드 없음.
- **권고**: `AlertPolicyPanel`과 같은 `let live = true` 가드를 추가하거나, `variantId`를 클로저로 캡처해 `setPurchases`/`setError` 직전에 `variantId === latestVariantId`를 확인한다.

### C3-03 — UsedComparisonPage도 같은 패턴의 응답 레이스가 있다 · High
- **위치**: `web/src/used/UsedComparisonPage.tsx:130-142`
- **근거**:
  ```tsx
  const reload = () => {
    if (productId === null) return
    api
      .getComparison(productId)
      .then(setComparison)
      .catch(() => setError('비교표를 불러오지 못했습니다.'))
  }

  useEffect(() => {
    setComparison(null)
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId])
  ```
  `eslint-disable` 주석이 있는 걸 보면 `reload`를 deps에서 의도적으로 뺀 것은 인지하고 있으나, **레이스 가드 자체가 없다는 사실은 다루지 않았다.** `productId`를 빠르게 바꾸면(제품 셀렉트를 연속 조작) C3-02와 동일한 구조로 옛 product의 `getComparison` 응답이 늦게 도착해 **다른 제품의 비교표**가 화면에 남을 수 있다.
- **실패 시나리오**: 사용자가 제품 A → 제품 B로 셀렉트를 빠르게 바꾼다. A의 `getComparison` 응답이 B보다 늦게 도착하면, 셀렉트는 B를 가리키는데 표엔 A의 매물·축값·메모가 뜬다. 중고 매물 비교는 가격·컨디션을 나란히 보고 고르는 화면이라, 다른 제품의 매물이 섞이면 오판 위험이 크다.
- **이미 막는 장치 확인**: `web/src/used/UsedComparisonPage.test.tsx` 전체를 확인했다 — 모든 테스트가 제품 하나만 선택하고 끝난다. 연속 전환·레이스 테스트 없음. 가드 없음.
- **권고**: C3-02와 동일 — `live` 플래그 또는 최신 `productId` 비교 가드.

## Medium

### C3-04 — 쿠팡 관측 시각이 `shared/kst.ts`를 우회해 UTC 날짜로 어긋난다 · Medium
- **위치**: `web/src/decision/present.ts:97`
- **근거**:
  ```ts
  const observed = price.observedAt === null ? '' : ` (관측 ${price.observedAt.slice(0, 10)})`
  ```
  `web/src/shared/kst.ts`의 문서화된 전제와 정면으로 어긋난다:
  > "ISO 문자열을 `slice(0, 10)`으로 자르면 **UTC 날짜**가 나온다. `2026-07-01T20:00:00Z`는 한국에서 이미 7월 2일 새벽 5시인데 화면엔 7월 1일이 뜬다. 하루가 통째로 어긋난다."
  `coupangPriceLine`은 정확히 이 패턴(`.slice(0, 10)`)을 `kstDate()`를 거치지 않고 직접 쓴다. `observedAt`이 UTC 15:00~23:59 사이(한국시각 자정을 넘겨 다음날이 되는 구간, 하루의 약 37.5%)에 찍히면 화면에 뜨는 날짜가 실제 KST 관측일보다 하루 이르게 표시된다.
- **실패 시나리오**: 쿠팡 크롬 확장이 `2026-07-20T20:00:00Z`(KST로는 `2026-07-21 05:00`)에 관측을 보내면, 화면엔 "쿠팡 정가 899,000원 (관측 2026-07-20)"으로 뜬다. 실제로는 7/21 새벽 관측인데 7/20으로 보여 "관측이 하루 지났다"는 신선도 판단이 틀릴 수 있다.
- **이미 막는 장치 확인**: `web/src/decision/present.test.ts:291-298`의 `coupangPriceLine` 테스트는 `observedAt: '2026-07-20T00:00:00Z'`(UTC 00:00 → KST 09:00, 같은 날)만 쓴다 — 날짜 경계를 넘지 않아 이 버그를 못 잡는다. `web/src/decision/DecisionPage.test.tsx`의 CMP-01 테스트(`'2026-07-20T00:00:00Z'`)도 같은 시각을 쓴다. 경계를 넘는 시각으로 테스트한 케이스는 없다.
- **권고**: `price.observedAt.slice(0, 10)` 대신 `kstDate(price.observedAt)`을 쓴다(이미 `present.ts`가 `kstDate`를 다른 파일에서 재수출하는 정본을 두고 있다).

## types.ts ↔ core 응답 대조 결과
`core/src/main/java/dev/hogumeter/core/adapter/web/*.java`와 그 유스케이스 반환 record(총 20개 타입: `BenchmarkView`+중첩 `PricePoint`/`DealRef`/`Gap`, `SignalView`, `CadenceView`, `PurchaseObservation`/`ObservationContext`/`ReportCard`, `AlertPolicyView`/`UpdateRequest`, `ReviewQueueItem`(core `PendingItem`), `WatchItemView`/`BoughtPrefill`/`PinCreated`, `ComparisonView`/`ComparisonRow`/`EvaluationResponse`/`PriceContext`/`RiskSignal`, `PrioritizedProduct`, `CoupangLatestPrice`(core `LatestPriceResponse`), `ProductSummary`/`VariantView`/`Axis`, `ApiError` 코드 카탈로그)를 필드 단위로 대조했다.

**필드명·타입·null 허용 여부가 실제로 어긋나는 항목은 없었다.** 다만 두 가지 완전성 차이를 남긴다(런타임 결함 아님, Info로 분류):

| 타입/필드 | web 선언 | core 실제 | 일치 |
|---|---|---|---|
| `ReviewQueueItem` | `id, type, occurrences, firstSeenAt, lastSeenAt, sourceUrl, subject, candidateProducts, conditions, payload` | core `GetReviewQueueUseCase.PendingItem`엔 위 필드 전부 + **`digestAppearances: int`**(DIG-04 ⑤ "N회째 미확인"의 재료, `GetReviewQueueUseCase.java:198,210`)가 추가로 있다 | ⚠️ web 타입에 `digestAppearances` 필드 자체가 없음 — 다이제스트(docs/18)가 아직 2차 기능이라 지금은 소비처가 없어 기능적 결함은 아니지만, JSON엔 실려 오는데 web 타입이 모른다 |
| `ApiError.code` union | 16개 리터럴 + `(string & {})` 이스케이프 | `ApiExceptionHandler`가 실제로 던지는 코드는 22개(`BM_DEMAND_AXIS_VALUE_REQUIRED`, `INVALID_COUPANG_OBSERVATION`, `EXTENSION_AUTH_FAILED`, `RATE_LIMIT_EXCEEDED`, `PURCHASE_NOT_FOUND`, `PUR_ILLEGAL_TRANSITION` 6개가 union에 없음) | `(string & {})` 이스케이프가 있어 컴파일·런타임 모두 문제없이 그 코드를 그대로 표시한다(`ApiFailure.code`는 애초에 `string`) — 문서적 완전성 차이일 뿐 |

그 외 `BenchmarkView`(및 `Gap.Leg`), `SignalView`, `CadenceView`, `PurchaseObservation`/`ObservationContext`/`ReportCard`, `AlertPolicyView`/`UpdateRequest`, `WatchItemView`/`BoughtPrefill`/`PinCreated`, `ComparisonView`/`ComparisonRow`, `EvaluationResponse`/`PriceContext`/`RiskSignal`, `PrioritizedProduct`, `CoupangLatestPrice`, `ProductSummary`/`VariantView`/`Axis`는 필드명·nullable 표기(`Long`→`number|null`, `@JsonInclude(NON_NULL)`→optional, `List<T>`→`T[]`, `Map<Long,String>`→`Record<string,string>`)가 전부 core 원문과 일치했다.

## 검토했으나 문제없음 (근거)
- **`?? 0` / `|| 0` / `|| ''` sentinel**: `web/src/**` 전수 grep(`\?\?\s*0|\|\|\s*0\b|\|\|\s*''|\|\|\s*""`) 결과 0건. `AlertPolicyPanel.toForm`도 "빈 값은 빈 칸, `0`으로 채우면 '공짜여야 알림'이 된다"는 주석과 함께 `undefined`를 그대로 빈 문자열로만 다룬다.
- **`currentPrice`/`gap` 미확립(null) 처리**: `decision/present.ts`의 `gapLine`·`lowestLine`·`verdictSubline`·`Gauge.tsx`가 전부 `currentPrice === null`/`leg === null`을 한 곳(`present.ts`)에서 판별하고, `present.test.ts`가 SPARSE/NONE에서 금액 정규식(`PRICE_AMOUNT`)이 안 나오는 것을 단언한다.
- **`shared/kst.ts` 자체**: 오프셋 계산·`kstDate`/`todayKst`는 `kst.test.ts`로 KST 하루 밀림 회귀를 정확히 잡고 있다. `purchase/buildPurchaseCommand.ts`도 문자열에 `+09:00` 오프셋을 박아 타임존 파싱 함정을 피한다(`buildPurchaseCommand.test.ts`로 확인).
- **`AlertPolicyPanel`·`WatchPage`·`UsedSearchPage`·`ReviewQueuePage`·`PriorityPage`의 fetch effect**: 전부 `let live = true` + cleanup 가드가 있다(C3-02·C3-03과 대비되는 올바른 패턴).
- **`App.tsx`의 탭 전환·구매 프리필 리셋**: `openDecision`이 `setPurchasePrefill(null)`로 이전 핀의 프리필이 새 variant로 새지 않게 막고, `DecisionPage`도 `prefill={variantId === initialVariantId ? purchasePrefill : null}`로 사용자가 셀렉트를 직접 바꾸면 프리필을 버린다.
- **`client.ts`의 에러 처리**: `request`/`command` 둘 다 비-2xx에서 `{code,message}` 파싱을 시도하고 실패하면 `HTTP_{status}`로 살린다(삼키지 않음). 각 페이지(`RegistrationPage`, `PurchasePanel`, `AlertPolicyPanel`, `WatchPage`, `ReviewQueuePage` 등)가 전부 `describe(failure)` 패턴으로 `role="alert"` 문구를 그린다 — 빈 화면·무한 로딩으로 삼켜지는 경로 없음.
- **`buildCommand.ts`/`buildPurchaseCommand.ts`/`buildPolicyCommand.ts`/`buildUsedSearchCommand.ts`**: 숫자 파싱 전부 `digits` 정규식 + `Number(...) <= 0` 가드를 쓰고, 빈 문자열은 `null`로(0이 아니라) 변환한다. 대응 `*.test.ts`가 각 규칙을 검증한다.

## 시간·범위 한계로 못 본 것
- `Gauge.tsx`·`ThemeToggle.tsx`는 테스트가 없다는 사실을 확인했지만(과제 배경대로), 코드를 읽은 결과 명백한 기능적 결함(키보드 도달 불가·스크린리더 값 누락 등)은 찾지 못했다 — 둘 다 `aria-hidden`이라 스크린리더 영향 범위가 제한적이다. 실제 브라우저·리듀스모션 환경에서의 시각적 애니메이션 타이밍은 정적 리뷰로 검증 불가.
- `web/src/api/client.test.ts`는 열지 않았다(요청/커맨드 래퍼 자체 테스트) — `request`/`command`의 구현을 직접 읽어 계약을 확인했으므로 낮은 우선순위로 건너뛰었다.
- core `AlertPolicyController`·`ComparisonController`·`CoupangObservationController` 등은 Explore 서브에이전트의 인용을 통해 대조했다 — 서브에이전트 결과 자체를 재검증하기 위해 원문 일부(`BenchmarkView`, `ReviewQueueItem`/`digestAppearances`)만 직접 Grep으로 재확인했고, 나머지 record는 인용된 원문 형태(필드명·박싱 여부)를 그대로 신뢰했다. 시간상 core 파일 20개 전부를 이 세션에서 직접 재열람하지는 않았다.
- 실제 브라우저에서 C3-01~03을 재현 스크린샷으로 남기진 않았다(코드 정적 추적 + React state bail-out 표준 동작 근거로만 확인). `bash scripts/smoke.sh`나 `npm test`를 이 리뷰에서 실행하지 않았다(리뷰 규칙상 코드 실행 없이 정적 검토만 수행).
