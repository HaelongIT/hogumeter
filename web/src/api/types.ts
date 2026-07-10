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
 * ⚠️ `currentPrice`는 **0일 수 있다** — core의 `StubCurrentPriceProvider`가 네이버 키
 * 미발급 상태에서 0을 반환한다(docs/91 Q-3·Q-53). 0은 가격이 아니라 **미확립 표식**이고,
 * 그때 `gap`은 `0 − 기준가` = 큰 음수가 된다. 그대로 그리면 "100% 싸다"는 거짓말이다.
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
  currentPrice: number
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

/** GET /api/v1/variants/{variantId}/purchases */
export interface PurchaseObservation {
  purchaseId: number
  state: PurchaseState
  paidPrice: number
  purchasedAt: string
  context: ObservationContext
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

export type ReviewQueueType = 'UNCLASSIFIED' | 'OUTLIER_LOWER' | 'KEYWORD_SUGGEST'

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
  code: 'BM_VARIANT_NOT_FOUND' | 'BM_INVALID_PERIOD' | 'REG_INVALID_ALERT_POLICY' | (string & {})
  message: string
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
}

/** PUT 본문. 부재는 **null**로 명시한다 — 키를 빼면 core가 "기간 P 누락"으로 400을 낸다. */
export interface UpdateAlertPolicyCommand {
  targetPrice: number | null
  periodMonths: number
  quietHoursStart: number | null
  quietHoursEnd: number | null
}
