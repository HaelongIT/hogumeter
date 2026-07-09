import type { ApiError, ProductCreated, ProductSummary, RegisterProductCommand, VariantView } from './types'

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

export const api = {
  listProducts: () => request<ProductSummary[]>('/api/v1/products'),

  listVariants: (productId: number) => request<VariantView[]>(`/api/v1/products/${productId}/variants`),

  registerProduct: (command: RegisterProductCommand) =>
    request<ProductCreated>('/api/v1/products', {
      method: 'POST',
      body: JSON.stringify(command),
    }),
}
