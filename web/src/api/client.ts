import type {
  AlertPolicyView,
  ApiError,
  BenchmarkView,
  CadenceView,
  ProductCreated,
  ProductSummary,
  PurchaseObservation,
  PurchaseRecorded,
  RecordPurchaseCommand,
  RegisterProductCommand,
  ReviewQueueItem,
  SignalView,
  UpdateAlertPolicyCommand,
  VariantView,
} from './types'

/** core가 `{ code, message }`로 돌려준 실패. 그 외 실패는 `Error`. */
export class ApiFailure extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
  ) {
    super(`${code}`)
    this.name = 'ApiFailure'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })

  if (!response.ok) {
    // core는 도메인 예외만 { code, message }로 준다. 그 밖(400 바인딩 오류, 500)은
    // Spring 기본 형태라 파싱이 실패할 수 있다 — 삼키지 말고 상태 코드를 살린다.
    const error = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiFailure(response.status, error?.code ?? `HTTP_${response.status}`)
  }
  return (await response.json()) as T
}

/**
 * 본문 없는 명령(200 + 빈 본문). `request`는 항상 json을 파싱하므로 빈 본문에서 터진다.
 * 실패 해석은 `request`와 같다 — core의 `{code, message}`를 살리고, 아니면 `HTTP_{status}`로.
 */
async function command(path: string, init?: RequestInit): Promise<void> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })

  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as ApiError | null
    throw new ApiFailure(response.status, error?.code ?? `HTTP_${response.status}`)
  }
}

export const api = {
  listProducts: () => request<ProductSummary[]>('/api/v1/products'),

  listVariants: (productId: number) => request<VariantView[]>(`/api/v1/products/${productId}/variants`),

  registerProduct: (command: RegisterProductCommand) =>
    request<ProductCreated>('/api/v1/products', {
      method: 'POST',
      body: JSON.stringify(command),
    }),

  // 조회 3종. variant가 없으면 BM_VARIANT_NOT_FOUND로 실패한다.
  getBenchmark: (variantId: number, periodMonths = 6) =>
    request<BenchmarkView>(`/api/v1/variants/${variantId}/benchmark?periodMonths=${periodMonths}`),

  getSignal: (variantId: number) => request<SignalView>(`/api/v1/variants/${variantId}/signal`),

  getCadence: (variantId: number, periodMonths = 6) =>
    request<CadenceView>(`/api/v1/variants/${variantId}/cadence?periodMonths=${periodMonths}`),

  // PUR — 구매 기록(쓰기)과 관찰 문맥(읽기).
  listPurchases: (variantId: number) => request<PurchaseObservation[]>(`/api/v1/variants/${variantId}/purchases`),

  recordPurchase: (command: RecordPurchaseCommand) =>
    request<PurchaseRecorded>('/api/v1/purchases', {
      method: 'POST',
      body: JSON.stringify(command),
    }),

  // REG-03 알림 정책. 미설정 variant도 200(`configured:false`) — 404는 variant가 없다는 뜻이다.
  getAlertPolicy: (variantId: number) => request<AlertPolicyView>(`/api/v1/variants/${variantId}/alert-policy`),

  updateAlertPolicy: (variantId: number, command: UpdateAlertPolicyCommand) =>
    request<AlertPolicyView>(`/api/v1/variants/${variantId}/alert-policy`, {
      method: 'PUT',
      body: JSON.stringify(command),
    }),

  // 미상 큐 — 조회 + 승격·기각(Q-15). 처리하면 그 항목은 PENDING에서 내려가 목록에서 사라진다.
  listReviewQueue: () => request<ReviewQueueItem[]>('/api/v1/review-queue'),

  /** 승격 — 이상치 오탐을 정상으로(표본 복귀). 미상 항목은 core가 400으로 막는다(variant 지정 필요). */
  promoteReviewItem: (id: number) => command(`/api/v1/review-queue/${id}/promote`, { method: 'POST' }),

  /** 기각 — 사기·낚시로 영구 제외(재수집돼도 표본 복귀 없음). 미상 항목은 큐에서 내리기만 한다. */
  rejectReviewItem: (id: number) => command(`/api/v1/review-queue/${id}/reject`, { method: 'POST' }),
}
