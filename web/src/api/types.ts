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

/** ApiExceptionHandler가 돌려주는 유일한 에러 형태. 코드는 현재 2종뿐이다. */
export interface ApiError {
  code: 'BM_VARIANT_NOT_FOUND' | 'BM_INVALID_PERIOD' | (string & {})
  message: string
}
