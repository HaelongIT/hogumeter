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

/** ApiExceptionHandler가 돌려주는 유일한 에러 형태. 코드는 현재 2종뿐이다. */
export interface ApiError {
  code: 'BM_VARIANT_NOT_FOUND' | 'BM_INVALID_PERIOD' | (string & {})
  message: string
}
