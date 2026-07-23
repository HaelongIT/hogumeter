/**
 * core REST 계약. **실제 컨트롤러/도메인 record를 읽고 옮긴 것**이지 추측이 아니다.
 * 출처: core/src/main/java/dev/hogumeter/core/adapter/web/*.java
 *
 * 응답은 봉투 없이 리소스를 직접 반환한다(docs/91 Q-2 확정). 에러만 { code, message }.
 * 통계 필드가 null인 것은 버그가 아니라 **도메인 계약**이다 — 표본이 빈약하면 통계 용어를
 * 쓰지 않는다(절대 원칙 1 정직성). 그래서 아래 타입들은 null을 감추지 않는다.
 */

export type DemandAxisMode = 'GROUPED' | 'SPLIT'
export type AxisType = 'PRICE' | 'DEMAND'

/** GET /api/v1/products */
export interface ProductSummary {
  productId: number
  name: string
  category: string | null
  demandAxisMode: DemandAxisMode
  /**
   * 축 정의(Q-66 ②). variant 라벨은 **가격축 조합만** 보여 주므로, 이게 없으면 사람은 자기가 어느 축을
   * 수요축으로 등록했는지 확인할 길이 없다(수요축은 variant를 안 나눠 흔적이 없다).
   */
  axes: Axis[]
  variants: VariantView[]
}

/** GET /api/v1/products/{id}/variants */
export interface VariantView {
  variantId: number
  label: string
  priceAxisValues: Record<string, string>
}

/** POST /api/v1/products */
export interface RegisterProductCommand {
  name: string
  category: string
  demandAxisMode: DemandAxisMode
  axes: Axis[]
  variants: VariantSpec[]
  aliases: string[]
}

export interface Axis {
  axisType: AxisType
  name: string
  allowedValues: string[]
}

export interface VariantSpec {
  label: string
  priceAxisValues: Record<string, string>
}

export interface ProductCreated {
  productId: number
}

/** 표본 3단 판정(BM-06). SPARSE/NONE에서 통계 필드가 null인 것은 계약이다. */
export type Tier = 'SUFFICIENT' | 'SPARSE' | 'NONE'

/** SIG-01. GRAY = 표본 부족이라 색을 낼 수 없음(빨간불이 아니다). */
export type SignalColor = 'GREEN' | 'YELLOW' | 'RED' | 'GRAY'

export interface PricePoint {
  price: number
  date: string
}

export interface DealRef {
  price: number
  date: string
  site: string
  sourceUrl: string
  /** BM-02 조건 태그(`카할` 등). 이 사례를 "정상 가격"으로 오인하지 않게 병기한다(Q-46 ①). 없으면 빈 배열. */
  conditions: string[]
}

/** 현재가 − 참조가. 참조가가 없으면 leg 자체가 null. */
export interface GapLeg {
  won: number
  pct: number
}

export interface Gap {
  vsBenchmark: GapLeg | null
  vsLowest: GapLeg | null
}

/**
 * GET /api/v1/variants/{variantId}/benchmark?periodMonths=&includeOutliers=
 *
 * ⚠️ `currentPrice`는 **null일 수 있다** — core의 `StubCurrentPriceProvider`가 네이버 키
 * 미발급 상태에서 null(미확립)을 반환한다(docs/91 Q-3·Q-53). 그때 `gap`의 두 leg도 null이다
 * (core가 갭을 계산하지 않는다). 예전엔 0을 sentinel로 써서 `gap = 0 − 기준가` = −100%가 왔고,
 * 그대로 그리면 "100% 싸다"는 거짓말이었다. 이제 미확립은 null이라 타입이 그 사실을 감추지 못한다.
 * 해석은 `decision/present.ts`의 `gapLine` 한 곳에만 둔다(refactor seam).
 */
export interface BenchmarkView {
  tier: Tier
  benchmarkPrice: number | null
  goodDealLine: number | null
  periodLowest: PricePoint | null
  latestDeal: DealRef | null
  n: number
  m: number
  expandedToMonths: number | null
  currentPrice: number | null
  gap: Gap
  cases: DealRef[]
}

/** GET /api/v1/variants/{variantId}/signal — 표시 전용. 알림 트리거가 아니다. */
export interface SignalView {
  color: SignalColor
  goodDealLineEstablished: boolean
  notes: string[]
}

/** GET /api/v1/variants/{variantId}/cadence?periodMonths= — "예상일" 필드는 없다(예측 금지). */
export interface CadenceView {
  eventCount: number
  intervalMedianDays: number | null
  elapsedDays: number | null
  observedMonths: number
  guardMet: boolean
}

/** PUR-01 구매 관찰 상태기계(docs/15). */
export type PurchaseState = 'OBSERVING' | 'REPORT_PENDING' | 'CLOSED' | 'ARCHIVED'

/**
 * PUR-05 관찰 문맥. **세 모드 배타** — 모드 밖 필드는 null이다(도메인 계약).
 * `overpaidWon` 양수 = 내가 더 비싸게 샀다.
 */
export interface ObservationContext {
  mode: 'ACTIVE_DEAL' | 'NO_ACTIVE_DEAL' | 'REPORT_PENDING'
  activeLowestPriceLast: number | null
  overpaidWon: number | null
  overpaidPct: number | null
  observationDay: number | null
  cheaperChanceCount: number | null
}

/**
 * PUR-04 성적표 — 관찰이 끝나 발급된(CLOSED) 구매의 "호구였나" 판정. 유머 등급·라벨 없음(정직성).
 * `unobserved`(관측 시작 이전 구매)거나 `n === 0`이면 통계 필드(percentile·lowestOpportunity)는 null이다 —
 * 표시 계층 재량이 아니라 도메인 계약. `paidGap`은 구매가 − 동결 기준가(양수=기준가보다 비쌈), 기준가 부재 시 null.
 */
export interface ReportCard {
  unobserved: boolean
  n: number
  cheaperCount: number
  percentile: number | null
  lowestOpportunity: number | null
  paidPrice: number
  paidGap: number | null
}

/** GET /api/v1/variants/{variantId}/purchases */
export interface PurchaseObservation {
  purchaseId: number
  state: PurchaseState
  paidPrice: number
  purchasedAt: string
  context: ObservationContext
  /** 발급된 성적표(CLOSED만). 그 외 상태는 null — 아직 발급 안 됨을 그대로 노출한다. */
  reportCard: ReportCard | null
}

/** POST /api/v1/purchases — `observationDays`가 null이면 core가 90일을 적용한다. */
export interface RecordPurchaseCommand {
  variantId: number
  demandAxisValue: string | null
  paidPrice: number
  purchasedAt: string
  observationDays: number | null
  linkedDealEventId: number | null
}

export interface PurchaseRecorded {
  purchaseId: number
}

export type ReviewQueueType = 'UNCLASSIFIED' | 'OUTLIER_LOWER' | 'KEYWORD_SUGGEST' | 'DEMAND_UNKNOWN'

/**
 * GET /api/v1/review-queue (읽기 전용) — 처리 대기(PENDING) 항목만. 같은 근거는 하나로 접혀 온다.
 *
 * **승격·기각 REST는 없다**(docs/91 Q-15). `payload`는 유형별 근거를 그대로 담은 jsonb라
 * 필드가 보장되지 않는다 — `review/present.ts`가 한 곳에서 해석한다.
 * `sourceUrl`은 원문을 잇지 못하면 `null`이다(빈 문자열이 아니다 — 죽은 링크를 그리지 않으려고).
 *
 * `occurrences > 1`은 **재처리 멱등이 없다는 뜻**이다 — 매칭 실패 원문은 매 틱마다 다시 큐에 쌓인다
 * (docs/91 Q-27 ④). 숨기지 않고 세어서 보여준다.
 */
export interface ReviewQueueItem {
  id: number
  type: ReviewQueueType
  occurrences: number
  firstSeenAt: string
  lastSeenAt: string
  sourceUrl: string | null
  /** 이 항목이 **무엇에 대한 것인가**(`제품 — variant`). 미상 항목은 정의상 `null`이다. */
  subject: string | null
  /** 후보 제품 이름. 사라진 제품은 `#id`로 온다 — 조용히 빠지지 않는다. */
  candidateProducts: string[]
  /**
   * 이 딜의 조건 태그(BM-02 AC-2). **이상치가 왜 싸 보이는지**를 말한다 —
   * `카할`이면 특정 카드 보유자만 그 가격이고, `배송비미상`이면 저장된 값이 하한이다.
   * 즉 이상치가 아니라 정상일 수 있다. 미상 항목은 딜이 없으므로 항상 빈 배열.
   */
  conditions: string[]
  payload: Record<string, unknown>
}

/** 에러 형태는 하나다(`{code, message}`). 코드 카탈로그는 `docs/benchmark/07`. */
export interface ApiError {
  code:
    | 'BM_VARIANT_NOT_FOUND'
    | 'BM_INVALID_PERIOD'
    | 'REG_INVALID_ALERT_POLICY'
    | 'REG_INVALID_PRODUCT'
    // Q-15 승격·기각. NOT_FOUND는 "없는 id"와 "이미 처리됨"을 함께 뜻한다(둘 다 지금 큐에 없다).
    | 'REVIEW_ITEM_NOT_FOUND'
    | 'REVIEW_PROMOTE_UNSUPPORTED'
    // USED-04·05
    | 'USED_SEARCH_NOT_FOUND'
    | 'LISTING_NOT_FOUND'
    | 'COMPARISON_AXIS_NOT_FOUND'
    | (string & {})
  message: string
}

/**
 * GET /api/v1/alerts/status (AL-05) — 알림이 실제로 사용자에게 발송되는가.
 * `delivering: false`면 스텁이라 로그로만 남는다(텔레그램 미설정). 화면이 그 사실을 밝혀 "목표가만 설정하면
 * 알림이 온다"는 과대약속을 막는다(절대 원칙 6).
 */
export interface AlertStatus {
  delivering: boolean
}

/**
 * GET/PUT /api/v1/variants/{id}/alert-policy (REG-03)
 *
 * core의 `AlertPolicyView`는 `@JsonInclude(NON_NULL)`이라 **null인 필드는 키 자체가 없다.**
 * 그래서 `number | null`이 아니라 optional이다 — "값이 null"과 "키가 없음"을 섞으면 화면이 거짓말한다.
 *
 * `configured: false`면 나머지가 전부 없다. 알림 판정이 쓰는 기본 기간(6개월)은 core의 private 상수라
 * 이 응답에 실리지 않는다(docs/91 Q-48) — 화면이 지어내 채우면 그 값이 세 번째 사본이 된다.
 */
export interface AlertPolicyView {
  configured: boolean
  targetPrice?: number
  periodMonths?: number
  quietHoursStart?: number
  quietHoursEnd?: number
  /**
   * 기준가 라벨 임계 K(3~10). **미설정이라도 숫자로 온다** — 기본값의 정본이 core의 상수 하나라
   * 사본이 생기지 않기 때문이다(기간 P는 아직 그 정본이 없어 미설정이면 부재, docs/91 Q-48 ②).
   */
  kDisplay?: number
  /**
   * 제외 키워드(리퍼·벌크 등, Q-28). **항상 배열로 온다** — 없으면 `[]`다(core `AlertPolicyView`가 null이
   * 아니라 빈 목록을 내므로 `NON_NULL`이 지우지 못한다). 걸리는 딜은 기준가·신호·알림 전 통계에서 빠진다.
   */
  excludeKeywords: string[]
}

/**
 * PUT 본문. 부재는 **null**로 명시한다 — 키를 빼면 core가 "기간 P 누락"으로 400을 낸다.
 *
 * ⚠️ PUT은 **전체 교체**다. `kDisplay`를 빼면 core가 기본값(5)으로 되돌린다 — 화면이 K를 안 보내면
 * 저장할 때마다 사용자가 고른 K가 조용히 리셋된다. 그래서 항상 보낸다. `excludeKeywords`도 같은 이유로
 * 항상 보낸다(빼면 빈 목록으로 되돌아가 사용자가 넣은 키워드가 조용히 사라진다).
 */
export interface UpdateAlertPolicyCommand {
  targetPrice: number | null
  periodMonths: number
  quietHoursStart: number | null
  quietHoursEnd: number | null
  kDisplay: number
  excludeKeywords: string[]
}

/**
 * GET/PUT /api/v1/settings/exclude-keywords — **전역** 제외 키워드(Q-28 ①).
 * 모든 variant에 함께 적용된다(제품별 목록과 합집합). 미설정이면 빈 배열이다.
 */
export interface GlobalExcludeKeywordsView {
  excludeKeywords: string[]
}

// ── USED-01~05 중고 (M2) ──────────────────────────────────────────

export type BonusMode = 'SORT' | 'TRIGGER'

export interface BonusGroupInput {
  keywords: string[]
  mode: BonusMode
}

/** POST /api/v1/products/{productId}/used-searches. platform은 core가 v1 BUNJANG로 고정한다. */
export interface RegisterUsedSearchCommand {
  required: string[]
  bonusGroups: BonusGroupInput[]
  exclude: string[]
  targetPrice: number | null
  pollIntervalMin: number | null
}

export interface UsedSearchCreated {
  usedSearchId: number
}

/**
 * GET /api/v1/products/{productId}/used-searches — 등록된 중고 검색 조회. 없는 제품은 빈 배열
 * (404 아님, variant 조회와 같은 계약). `pollIntervalMin`은 항상 하한(10) 이상으로 온다.
 */
export interface UsedSearchView {
  usedSearchId: number
  platform: string
  required: string[]
  exclude: string[]
  targetPrice: number | null
  pollIntervalMin: number
  bonusGroups: BonusGroupInput[]
}

export type EvaluationKind = 'URL' | 'TEXT' | 'MANUAL'

/** POST /api/v1/used-searches/{id}/evaluate 요청. kind별로 필요한 필드만 채운다. */
export interface EvaluationRequest {
  kind: EvaluationKind
  text: string | null
  title: string | null
  price: number | null
  url: string | null
  variantId: number | null
}

export interface EvaluatedListing {
  title: string
  price: number
  url: string | null
}

/**
 * USED-04 AC-13 ① — 기준가를 합성하지 않는다. `benchmarkComparisonPercent`는 `variantId`를
 * 준 요청에서만 채워진다(안 주면 null — "비교 안 함"이지 "0%"가 아니다). `activeSnapshotPrices`는
 * 통계 가공 없이 그대로 나열된다.
 */
export interface PriceContext {
  benchmarkComparisonPercent: number | null
  activeSnapshotPrices: number[]
  source: string
}

/** AC-14 — 나열만. "사기다"·"위험하다" 같은 판정 문구는 여기 없다(절대 원칙 2). */
export interface RiskSignal {
  category: string
  detail: string
}

/**
 * `needsInput`이 있으면 나머지는 전부 null — 이 입력으로는 못 읽었으니 그 종류로 다시 요청하라는 뜻.
 * (URL→TEXT→MANUAL 폴백, docs/used/04 AC-12)
 */
export interface EvaluationResponse {
  needsInput: EvaluationKind | null
  listing: EvaluatedListing | null
  priceContext: PriceContext | null
  riskSignals: RiskSignal[] | null
}

/** PUT /api/v1/products/{productId}/comparison-axes — 추가 전용(기존 축을 지우지 않는다). */
export interface ComparisonAxis {
  id: number
  name: string
}

/**
 * GET /api/v1/products/{productId}/comparison. `axisValues`는 **승격 안 된 축의 키가 아예 없다** —
 * null 값이 아니다. "미확인"과 "빈 문자열"을 혼동하면 체크리스트가 거짓말한다. 키는 축 id를
 * 문자열로 표현한 것이다(JSON 객체 키는 항상 문자열).
 */
export interface ComparisonRow {
  listingId: number
  title: string
  price: number
  url: string | null
  axisValues: Record<string, string>
  notes: string[]
}

export interface ComparisonView {
  axes: ComparisonAxis[]
  rows: ComparisonRow[]
}

/** POST /api/v1/listings/{listingId}/notes */
export interface NoteCreated {
  noteId: number
}

/** POST /api/v1/listings/{listingId}/axis-values — 재승격(같은 축)은 값을 갱신한다. */
export interface AxisValueRequest {
  axisId: number
  value: string
}

