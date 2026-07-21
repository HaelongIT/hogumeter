import { describe, expect, it } from 'vitest'
import { InvalidForm } from '../registration/buildCommand'
import { buildPurchaseCommand, type PurchaseForm } from './buildPurchaseCommand'

const form = (over: Partial<PurchaseForm> = {}): PurchaseForm => ({
  variantId: 11,
  paidPrice: '899,000',
  purchasedDate: '2026-07-01',
  observationDays: '',
  demandAxisValue: null,
  ...over,
})

describe('buildPurchaseCommand', () => {
  it('날짜만 받으면 그 날의 23:59 KST를 발생 시각으로 삼는다 (V2 계약: 입력 계층 책임)', () => {
    expect(buildPurchaseCommand(form()).purchasedAt).toBe('2026-07-01T14:59:00.000Z') // 23:59 +09:00
  })

  it('UTC 자정으로 해석하지 않는다 — 그게 `new Date("2026-01-01")`의 기본값이다', () => {
    const built = buildPurchaseCommand(form({ purchasedDate: '2026-01-01' })).purchasedAt

    expect(built).toBe('2026-01-01T14:59:00.000Z') // 23:59 +09:00
    expect(built).not.toBe(new Date('2026-01-01').toISOString()) // 00:00Z — 하루를 통째로 잃는다
  })

  it('금액의 콤마를 걷어내고 숫자로 보낸다', () => {
    expect(buildPurchaseCommand(form()).paidPrice).toBe(899_000)
  })

  it('관찰 기간을 비우면 보내지 않는다 — core가 기본 90일을 적용한다', () => {
    expect(buildPurchaseCommand(form()).observationDays).toBeNull()
    expect(buildPurchaseCommand(form({ observationDays: '30' })).observationDays).toBe(30)
  })

  it('수요축 값은 판단 화면이 정한 값을 그대로 보낸다 (묶음=null, 분리=고른 값)', () => {
    // 자유 입력이 아니다 — DecisionPage가 이미 null/값을 확정해 넘긴다(Q-66 ③).
    expect(buildPurchaseCommand(form()).demandAxisValue).toBeNull()
    expect(buildPurchaseCommand(form({ demandAxisValue: '블랙' })).demandAxisValue).toBe('블랙')
  })

  it('연결 딜은 지어내지 않는다 — 화면에 입력이 없으므로 항상 null', () => {
    expect(buildPurchaseCommand(form()).linkedDealEventId).toBeNull()
  })

  it.each([
    ['', '실지불가'],
    ['0', '실지불가'],
    ['-5000', '실지불가'],
    ['공짜', '실지불가'],
  ])('실지불가가 %s이면 서버로 보내지 않는다', (paidPrice, hint) => {
    expect(() => buildPurchaseCommand(form({ paidPrice }))).toThrow(InvalidForm)
    expect(() => buildPurchaseCommand(form({ paidPrice }))).toThrow(hint)
  })

  it('구매일이 없으면 거절한다', () => {
    expect(() => buildPurchaseCommand(form({ purchasedDate: '' }))).toThrow(InvalidForm)
  })

  it('관찰 기간이 숫자가 아니거나 0 이하면 거절한다', () => {
    expect(() => buildPurchaseCommand(form({ observationDays: '0' }))).toThrow(InvalidForm)
    expect(() => buildPurchaseCommand(form({ observationDays: '이틀' }))).toThrow(InvalidForm)
  })

  it('variant를 고르지 않으면 거절한다', () => {
    expect(() => buildPurchaseCommand(form({ variantId: null }))).toThrow(InvalidForm)
  })
})
