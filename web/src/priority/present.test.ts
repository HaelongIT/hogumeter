import { describe, expect, it } from 'vitest'
import type { PrioritizedProduct } from '../api/types'
import { rankLabel, statusBadge } from './present'

const product = (overrides: Partial<PrioritizedProduct> = {}): PrioritizedProduct => ({
  productId: 1,
  name: '아이폰 17',
  priorityRank: null,
  waiting: true,
  manuallyCompleted: false,
  ...overrides,
})

describe('statusBadge', () => {
  it('대기 중이면 배지가 없다', () => {
    expect(statusBadge(product({ waiting: true }))).toBeNull()
  })

  it('대기 아니면 "구매됨/완료" — CLOSED·ARCHIVED·수동완료를 가르지 않는다(Q-82)', () => {
    expect(statusBadge(product({ waiting: false }))).toBe('구매됨/완료')
    expect(statusBadge(product({ waiting: false, manuallyCompleted: true }))).toBe('구매됨/완료')
  })
})

describe('rankLabel', () => {
  it('순번이 없으면 그 사실을 말한다 — 0으로 오인되지 않게', () => {
    expect(rankLabel(product({ priorityRank: null }))).toBe('순번 미지정')
  })

  it('순번이 있으면 숫자를 보여준다', () => {
    expect(rankLabel(product({ priorityRank: 3 }))).toBe('3순위')
  })
})
