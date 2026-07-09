import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from './client'

const respond = (status: number, body: unknown) =>
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  )

describe('api client', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('제품 목록을 그대로 돌려준다 (봉투 없음, Q-2)', async () => {
    respond(200, [{ productId: 1, name: 'x', category: null, demandAxisMode: 'GROUPED', variants: [] }])

    await expect(api.listProducts()).resolves.toHaveLength(1)
  })

  it('등록은 POST + JSON 바디', async () => {
    const fetchMock = respond(201, { productId: 7 })

    await expect(
      api.registerProduct({
        name: 'x',
        category: '',
        demandAxisMode: 'GROUPED',
        axes: [],
        variants: [],
        aliases: [],
      }),
    ).resolves.toEqual({ productId: 7 })

    const [, init] = fetchMock.mock.calls[0]!
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toMatchObject({ name: 'x' })
  })

  it('도메인 에러의 code를 보존한다', async () => {
    respond(404, { code: 'BM_VARIANT_NOT_FOUND', message: 'variant not found: 9' })

    await expect(api.listVariants(9)).rejects.toMatchObject({ status: 404, code: 'BM_VARIANT_NOT_FOUND' })
  })

  it('{code,message}가 아닌 실패도 삼키지 않는다', async () => {
    // core는 도메인 예외만 {code,message}로 준다. 바인딩 오류·500은 Spring 기본 형태다.
    respond(500, undefined)

    const failure = await api.listProducts().catch((error: unknown) => error)
    expect(failure).toBeInstanceOf(ApiFailure)
    expect(failure).toMatchObject({ status: 500, code: 'HTTP_500' })
  })
})
