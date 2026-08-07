# 코드리뷰 20260806 — 20. 프론트엔드 (web)

> 리뷰어 C3의 발견을 통합. 원시 산출물은 `raw/C3-web.md`, 반박 검증은 `rebuttal/`.
> **1차 = 정적 검증**이며 범위는 발견까지다 — 수정은 2차.

### FE-01 — variant를 GROUPED→SPLIT(축 미선택)으로 바꾸면 이전 variant의 판단 요약이 안 지워진다 · High · decision · ❌미해결
- **위치**: `web/src/decision/DecisionPage.tsx:115-142`
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
  GROUPED variant(축 없음)에서는 `demandAxisValue`가 계속 `null`이었으므로, SPLIT variant(축 미선택)로 전환해도 이 값 자체가 바뀌지 않는다. 조회 effect는 새 `variantId`·`demandAxis`로 재실행되지만 조건(`demandAxis !== null && demandAxisValue === null`)이 참이라 **`setLoaded(null)`·`setError(null)`에 도달하기 전에 조기 `return`한다.** 반박 검증에서 원문 라인 단위로 재확인했다(`rebuttal/C3-01.md`) — "React state bail-out" 표현 자체는 다소 부정확하지만("이 렌더에서 demandAxisValue는 애초에 null이었다"가 정확한 원인), 결론(조기 return이 `loaded`를 지우지 않는다)은 코드를 그대로 따라가면 재현된다.
- **영향**: `loaded`가 안 지워지므로 `{loaded && badge && (<section aria-label="판단 요약" ...>`가 그대로 참이 되어 **이전 variant(예: 아이폰)의 신호등·기준가·갭·주기가 새 variant(갤럭시) 화면에 그대로 남는다.** 로딩 스켈레톤(`{variantId !== null && !loaded && !error && (...)}`)도 `loaded !== null`이라 뜨지 않아 "로딩 중"이라는 신호조차 없다. "판단 요약" 섹션 안에는 제품/variant 이름을 표시하는 요소가 전혀 없어(신호등·기준가·갭·주기·최근 딜만 나열), 셀렉트가 정확히 바뀌었어도 요약 숫자가 stale이라는 걸 알아챌 단서가 섹션 자체엔 없다. `selectable()`이 GROUPED/SPLIT 구분 없이 모든 variant를 한 드롭다운에 평평하게 나열해 셀렉트 두 번이면 실사용 조작으로 재현된다. 절대 원칙 1(정직성 — "값이 없으면 없다고 보인다")에 정면으로 위배된다.
- **권고**: 두 `useEffect`를 하나로 합치거나, `demandAxisValue` 리셋 effect에서 `setLoaded(null)`·`setError(null)`도 함께 호출한다. 혹은 조회 effect의 조기 return 두 지점(`variantId === null`, `demandAxis !== null && demandAxisValue === null`) 모두에서 `setLoaded(null)`을 먼저 실행해 React state bail-out에 의존하지 않는 형태로 만든다.
- **출처**: `raw/C3-web.md` C3-01 · 반박 검증 `rebuttal/C3-01.md` → **CONFIRMED**

### FE-02 — PurchasePanel이 variant 전환 응답 레이스를 막지 않는다 · High · purchase · ❌미해결
- **위치**: `web/src/purchase/PurchasePanel.tsx:74-78`(effect), `68-72`(`reload`)
- **근거**:
  ```tsx
  useEffect(() => {
    setPurchases(null)
    setError(null)
    void reload(variantId)
  }, [variantId])
  ```
  `reload`는 `api.listPurchases(id).then(setPurchases)`뿐이고, cleanup 함수 자체가 없다(`return () => {...}`가 없음) — "가드가 빠졌다"가 아니라 취소 메커니즘이 통째로 부재. 같은 파일군의 `AlertPolicyPanel.tsx:79-98`·`WatchPage.tsx:45-66`·`UsedSearchPage.tsx:39-52`·`ReviewQueuePage.tsx`·`PriorityPage.tsx` 5개 파일 전부 `let live = true` + cleanup 가드를 실제로 쓴다는 것을 반박 검증에서 원문 대조로 확인했다(`rebuttal/C3-02.md`) — `PurchasePanel.tsx`만 이 패턴이 예외적으로 빠져 있다.
- **영향**: `DecisionPage.tsx:341-347`·`App.tsx:68` 어디에도 `key` prop이 없어 variant 전환은 재마운트가 아니라 순수 state 갱신 + effect 재실행으로 처리된다 — "재마운트가 무해하게 만든다"는 방어가 코드상 성립하지 않는다. `<select>`에 로딩 중 `disabled` 처리가 없어(`DecisionPage.tsx:152-163`) 사용자가 옵션을 연속 두 번 고르면 두 `listPurchases` 요청이 쉽게 겹친다. 겹치면 **먼저 보낸 요청(옛 variant)의 응답이 나중에 도착해 최종 화면을 덮어써**, 현재 선택된 variant와 다른 variant의 구매 기록이 고정된다. 구매 목록 항목(`PurchasePanel.tsx:143-166`)엔 variant 이름/ID 표시가 전혀 없어 사용자가 눈치챌 단서도 없다. "이미 샀는가/얼마에 샀는가"를 판단하는 성적표·관찰 문맥이 엉뚱한 variant 것으로 보이는 것이라, 절대 원칙 1·2(정직성·판단은 사람, 시스템은 근거)와 정면으로 부딪힌다. 유일한 관련 테스트(`PurchasePanel.test.tsx:124-130`)는 각 단계를 `waitFor`로 먼저 완료시킨 뒤 넘어가는 순차 시나리오라 out-of-order 응답을 재현하지 못한다.
- **권고**: `AlertPolicyPanel`과 동일한 `let live = true` 가드를 추가하거나, `variantId`를 클로저로 캡처해 `setPurchases`/`setError` 직전에 최신 `variantId`와 비교한다.
- **출처**: `raw/C3-web.md` C3-02 · 반박 검증 `rebuttal/C3-02.md` → **CONFIRMED**

### FE-03 — UsedComparisonPage도 같은 패턴의 응답 레이스가 있다 · High · used · ❌미해결
- **위치**: `web/src/used/UsedComparisonPage.tsx:130-142`
- **근거**:
  ```tsx
  const reload = () => {
    if (productId === null) return
    api.getComparison(productId).then(setComparison).catch(...)
  }
  useEffect(() => {
    setComparison(null)
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId])
  ```
  `AbortController`·`live` 가드 없음(파일 전체 확인). `eslint-disable` 주석은 exhaustive-deps 경고만 억제할 뿐 out-of-order 응답에 대한 방어와 무관하다. 반박 검증(`rebuttal/C3-03.md`)에서 구조 차이를 추가로 확인했다 — `productId`는 부모가 넘기는 prop(FE-02의 `variantId`)이 아니라 **컴포넌트 자신의 로컬 state**라, 부모가 `key`로 재마운트시켜 레이스를 지워줄 여지 자체가 원천적으로 없다(`UsedPage.tsx`의 `UsedEvaluatePage`엔 실제로 `key={evaluateRefreshKey}`가 있어 이 저장소가 그 패턴을 알고 쓰면서도 `UsedComparisonPage`엔 빠뜨렸다). `web/src/api/client.ts`도 `AbortSignal`을 받지 않아 상위에서 취소할 수단이 없다.
- **영향**: `<select>` `onChange`(마우스 연속 클릭 또는 방향키 연타)만으로 실사용 빈도로 재현 가능. `productId` 변경 즉시 `setComparison(null)`로 "불러오는 중..."을 보여준 뒤, **늦게 도착한 이전 product의 응답이 그 로딩 상태를 덮어써 고정**된다 — 로딩 문구가 사라지고 표가 뜨는 순간이 오히려 "방금 새로 불러왔다"는 착시를 만들어(반박 검증 지적), FE-02보다 가시성이 더 나쁘다. 비교표 헤더·셀엔 product 이름이 직접 안 나오고 매물 제목에 섞여 나올 뿐이라, 가격·축값·메모만 보면 다른 product의 표를 참고 정보로 그대로 쓸 수 있다. 중고 매물 비교는 가격·컨디션을 나란히 보고 사는 화면이라 오판 위험이 크다. `UsedComparisonPage.test.tsx` 전체(6개 테스트)가 단일 product·단일 `selectOptions` 호출만 검증해 이 경로를 못 잡는다.
- **권고**: FE-02와 동일 — `let live = true` 또는 최신 `productId` 비교 가드.
- **출처**: `raw/C3-web.md` C3-03 · 반박 검증 `rebuttal/C3-03.md` → **CONFIRMED**

### FE-04 — 쿠팡 관측 시각이 `shared/kst.ts`를 우회해 UTC 날짜로 어긋난다 · Medium · decision · ❌미해결
- **위치**: `web/src/decision/present.ts:97`
- **근거**:
  ```ts
  const observed = price.observedAt === null ? '' : ` (관측 ${price.observedAt.slice(0, 10)})`
  ```
  `web/src/shared/kst.ts`가 문서화한 전제("ISO 문자열을 `slice(0,10)`으로 자르면 UTC 날짜가 나온다 — 한국에서 이미 다음날인데 화면엔 하루 전으로 뜬다")와 정면으로 어긋난다. `coupangPriceLine`이 `kstDate()`를 거치지 않고 정확히 이 패턴을 직접 쓴다. `observedAt`이 UTC 15:00~23:59(하루의 약 37.5%, KST 자정을 넘겨 다음날이 되는 구간)에 찍히면 화면 날짜가 실제 KST 관측일보다 하루 이르게 표시된다.
- **영향**: 쿠팡 크롬 확장이 `2026-07-20T20:00:00Z`(KST `2026-07-21 05:00`)에 관측을 보내면 화면엔 "쿠팡 정가 899,000원 (관측 2026-07-20)"으로 떠 실제보다 하루 지난 관측처럼 보인다 — "관측이 오래됐다"는 신선도 판단이 틀릴 수 있다. `present.test.ts:291-298`·`DecisionPage.test.tsx`의 CMP-01 테스트 모두 날짜 경계를 넘지 않는 시각(`2026-07-20T00:00:00Z`)만 써서 이 버그를 못 잡는다. 반박 검증은 수행되지 않았다 — 원 심각도(Medium) 유지.
- **권고**: `price.observedAt.slice(0, 10)` 대신 `kstDate(price.observedAt)`을 쓴다(`present.ts`가 이미 다른 파일에서 `kstDate`를 정본으로 재수출하고 있다).
- **출처**: `raw/C3-web.md` C3-04 (반박 검증 미실시, 원 심각도 유지)

## 검토했으나 문제없음(통합)
- **`types.ts` ↔ core 응답 20개 타입 전수 대조**: `BenchmarkView`+`PricePoint`/`DealRef`/`Gap`, `SignalView`, `CadenceView`, `PurchaseObservation`/`ObservationContext`/`ReportCard`, `AlertPolicyView`/`UpdateRequest`, `ReviewQueueItem`, `WatchItemView`/`BoughtPrefill`/`PinCreated`, `ComparisonView`/`ComparisonRow`/`EvaluationResponse`/`PriceContext`/`RiskSignal`, `PrioritizedProduct`, `CoupangLatestPrice`, `ProductSummary`/`VariantView`/`Axis`, `ApiError` 코드 카탈로그 — **필드명·타입·null 허용 여부가 실제로 어긋나는 항목은 없었다.** 다음 리뷰가 이 전수 대조를 반복하지 않도록 여기 남긴다. 다만 런타임 결함은 아닌 완전성 차이 둘: (a) core `GetReviewQueueUseCase.PendingItem`엔 `digestAppearances: int`가 있으나 web `ReviewQueueItem` 타입엔 없음(다이제스트가 아직 2차 기능이라 현재 소비처 없음), (b) `ApiExceptionHandler`가 던지는 22개 코드 중 6개(`BM_DEMAND_AXIS_VALUE_REQUIRED` 등)가 web `ApiError.code` union(16개 리터럴)에 없으나 `(string & {})` 이스케이프가 있어 컴파일·런타임 모두 문제없음.
- **`?? 0` / `|| 0` / `|| ''` sentinel**: `web/src/**` 전수 grep 결과 0건.
- **`currentPrice`/`gap` 미확립(null) 처리**: `decision/present.ts` 한 곳에서만 판별하고 `present.test.ts`가 SPARSE/NONE 케이스를 단언.
- **`shared/kst.ts` 자체**: 오프셋 계산·`kstDate`/`todayKst`는 `kst.test.ts`로 KST 하루 밀림 회귀를 정확히 잡는다(FE-04는 이 정본을 우회한 소비처 쪽 결함).
- **`AlertPolicyPanel`·`WatchPage`·`UsedSearchPage`·`ReviewQueuePage`·`PriorityPage`의 fetch effect**: 전부 `let live = true` + cleanup 가드(FE-02·FE-03과 대비되는 올바른 패턴).
- **`App.tsx`의 탭 전환·구매 프리필 리셋**: `openDecision`이 `setPurchasePrefill(null)`로 이전 핀의 프리필이 새 variant로 새지 않게 막는다.
- **`client.ts`의 에러 처리**: 비-2xx에서 `{code,message}` 파싱 실패 시 `HTTP_{status}`로 살려 삼키지 않는다.
- **`buildCommand.ts`/`buildPurchaseCommand.ts`/`buildPolicyCommand.ts`/`buildUsedSearchCommand.ts`**: 숫자 파싱은 `digits` 정규식 + `Number(...) <= 0` 가드, 빈 문자열은 `0`이 아니라 `null`.

## 리뷰어별 원시 산출물
- `raw/C3-web.md` — C3-01~04(High 3 / Medium 1) + Info 2건(types.ts 완전성 차이)
