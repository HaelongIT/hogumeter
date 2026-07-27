import { describe, expect, it } from 'vitest'
import type { WatchItemView } from '../api/types'
import { dateLine, priceLine, stateLabel } from './present'

const item = (overrides: Partial<WatchItemView> = {}): WatchItemView => ({
  watchItemId: 1,
  dealEventId: 42,
  note: null,
  state: 'ACTIVE',
  pinnedAt: '2026-07-01T00:00:00Z',
  resolvedAt: null,
  currentPriceLast: 850_000,
  dealStatus: 'ACTIVE',
  ...overrides,
})

describe('priceLine', () => {
  it('현재가가 있으면 금액을 보여준다', () => {
    expect(priceLine(item({ currentPriceLast: 850_000 }))).toBe('현재가 850,000원')
  })

  it('딜이 사라져 현재가를 모르면 지어내지 않는다', () => {
    expect(priceLine(item({ currentPriceLast: null }))).toBe('현재가 미확인')
  })
})

describe('stateLabel', () => {
  it.each([
    ['ACTIVE', '관찰 중'],
    ['BOUGHT', '샀어요'],
    ['MISSED', '놓쳤어요(종료됨)'],
    ['DROPPED', '기각·해제'],
  ] as const)('%s → %s', (state, label) => {
    expect(stateLabel(state)).toBe(label)
  })
})

describe('dateLine', () => {
  it('결말난 핀은 결말 날짜를 보여준다', () => {
    expect(dateLine(item({ resolvedAt: '2026-07-10T00:00:00Z' }))).toBe('2026-07-10 결말')
  })

  it('활성 핀은 핀한 날짜를 보여준다 — 아직 결말이 없다', () => {
    expect(dateLine(item({ pinnedAt: '2026-07-01T00:00:00Z', resolvedAt: null }))).toBe('2026-07-01 핀')
  })
})
