import { describe, expect, it } from 'vitest'
import type { PurchaseObservation } from '../api/types'
import { kstDate, observationLine, stateLabel } from './present'

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

describe('kstDate — 저장은 UTC, 표시는 KST (OPS-03)', () => {
  it('UTC 저녁은 KST로 다음 날이다 — ISO 문자열을 그냥 자르면 하루가 어긋난다', () => {
    expect(kstDate('2026-07-01T20:00:00Z')).toBe('2026-07-02')
    expect('2026-07-01T20:00:00Z'.slice(0, 10)).toBe('2026-07-01') // 우리가 고치려는 그 버그
  })

  it('KST 자정 경계 (15:00Z)', () => {
    expect(kstDate('2026-07-01T14:59:59Z')).toBe('2026-07-01')
    expect(kstDate('2026-07-01T15:00:00Z')).toBe('2026-07-02')
  })

  it('우리 입력 경로가 쓰는 23:59 KST는 그대로 그 날이다', () => {
    // buildPurchaseCommand가 만드는 값(2026-07-01T23:59+09:00 = 14:59Z)
    expect(kstDate('2026-07-01T14:59:00.000Z')).toBe('2026-07-01')
  })

  it('연말 경계에서도 어긋나지 않는다', () => {
    expect(kstDate('2025-12-31T15:00:00Z')).toBe('2026-01-01')
  })

  it('실행 머신의 타임존에 의존하지 않는다 — 로컬 해석과 다를 수 있어야 한다', () => {
    // 로컬이 UTC든 KST든 결과가 같아야 한다. 오프셋을 명시해 계산하기 때문이다.
    const instant = '2026-07-01T20:00:00Z'
    expect(kstDate(instant)).toBe('2026-07-02')
    expect(kstDate(instant)).not.toBe(new Date(instant).toISOString().slice(0, 10))
  })
})
