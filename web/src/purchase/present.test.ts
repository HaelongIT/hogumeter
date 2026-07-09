import { describe, expect, it } from 'vitest'
import type { PurchaseObservation } from '../api/types'
import { observationLine, stateLabel } from './present'

const observation = (over: Partial<PurchaseObservation> = {}): PurchaseObservation => ({
  purchaseId: 1,
  state: 'OBSERVING',
  paidPrice: 899_000,
  purchasedAt: '2026-07-01T14:59:00Z',
  context: {
    mode: 'NO_ACTIVE_DEAL',
    activeLowestPriceLast: null,
    overpaidWon: null,
    overpaidPct: null,
    observationDay: 8,
    cheaperChanceCount: 0,
  },
  ...over,
})

describe('observationLine — 세 모드는 배타다. 모드 밖 필드를 그리지 않는다', () => {
  it('ACTIVE_DEAL: 지금 딜이 있고, 내가 얼마나 더 주고 샀는지 말한다', () => {
    const line = observationLine(
      observation({
        context: {
          mode: 'ACTIVE_DEAL',
          activeLowestPriceLast: 829_000,
          overpaidWon: 70_000,
          overpaidPct: 8.4,
          observationDay: null,
          cheaperChanceCount: null,
        },
      }),
    )
    expect(line).toContain('활성 딜 최저 829,000원')
    expect(line).toContain('70,000원 더 주고 샀음 (+8.4%)')
    expect(line).not.toContain('null')
    expect(line).not.toContain('일차')
  })

  it('ACTIVE_DEAL이지만 내가 더 싸게 샀으면 그렇게 말한다 (음수를 "더 주고"라 하지 않는다)', () => {
    const line = observationLine(
      observation({
        context: {
          mode: 'ACTIVE_DEAL',
          activeLowestPriceLast: 950_000,
          overpaidWon: -51_000,
          overpaidPct: -5.4,
          observationDay: null,
          cheaperChanceCount: null,
        },
      }),
    )
    expect(line).toContain('51,000원 싸게 샀음 (-5.4%)')
    expect(line).not.toContain('더 주고')
  })

  it('ACTIVE_DEAL에서 같은 값이면 상회분을 말하지 않는다', () => {
    const line = observationLine(
      observation({
        context: {
          mode: 'ACTIVE_DEAL',
          activeLowestPriceLast: 899_000,
          overpaidWon: 0,
          overpaidPct: 0,
          observationDay: null,
          cheaperChanceCount: null,
        },
      }),
    )
    expect(line).toContain('구매가와 같음')
  })

  it('NO_ACTIVE_DEAL: 관찰 일차와 놓친 기회 건수 — 0건도 감추지 않는다', () => {
    const line = observationLine(observation())
    expect(line).toContain('활성 딜 없음')
    expect(line).toContain('관찰 8일차')
    expect(line).toContain('더 싼 기회 0건')
    expect(line).not.toMatch(/\d{1,3}(,\d{3})+원/) // 이 모드엔 딜 가격이 없다
  })

  it('REPORT_PENDING: 아직 없는 성적을 지어내지 않는다', () => {
    const line = observationLine(
      observation({
        state: 'REPORT_PENDING',
        context: {
          mode: 'REPORT_PENDING',
          activeLowestPriceLast: null,
          overpaidWon: null,
          overpaidPct: null,
          observationDay: null,
          cheaperChanceCount: null,
        },
      }),
    )
    expect(line).toBe('성적 집계 중')
  })
})

describe('stateLabel', () => {
  it.each([
    ['OBSERVING', '관찰 중'],
    ['REPORT_PENDING', '성적 집계 중'],
    ['CLOSED', '성적표 발급'],
    ['ARCHIVED', '보관'],
  ] as const)('%s → %s', (state, label) => {
    expect(stateLabel(state)).toBe(label)
  })
})
