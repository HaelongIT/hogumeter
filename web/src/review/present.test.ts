import { describe, expect, it } from 'vitest'
import type { ReviewQueueItem } from '../api/types'
import { reviewLine } from './present'

const item = (over: Partial<ReviewQueueItem>): ReviewQueueItem => ({
  id: 1,
  type: 'UNCLASSIFIED',
  occurrences: 1,
  firstSeenAt: '2026-07-10T00:00:00Z',
  lastSeenAt: '2026-07-10T00:00:00Z',
  sourceUrl: null,
  payload: {},
  ...over,
})

describe('reviewLine', () => {
  it('미상은 무엇을 못 했는지 말한다 — 제목과 후보 수', () => {
    const line = reviewLine(
      item({ type: 'UNCLASSIFIED', payload: { title: '아이폰17 특가', productCandidates: [3, 7] } }),
    )

    expect(line.reason).toContain('어느 제품인지 확정하지 못했습니다')
    expect(line.detail).toContain('아이폰17 특가')
    expect(line.detail).toContain('후보 2개')
  })

  it('후보가 하나도 없으면 그렇게 말한다 — 0개를 숨기지 않는다', () => {
    const line = reviewLine(item({ payload: { title: '뭔가', productCandidates: [] } }))

    expect(line.detail).toContain('후보 0개')
  })

  /** 절대 원칙 2: 사기/최종 판단 로직을 만들지 않는다. "싸다"도 "위험하다"도 말하지 않는다. */
  it('이상치는 판단하지 않고 사실만 말한다', () => {
    const line = reviewLine(item({ type: 'OUTLIER_LOWER', payload: { priceFirst: 700000 } }))

    expect(line.detail).toContain('700,000원')
    expect(line.reason).toContain('분포 하단')
    expect(line.reason).not.toMatch(/사기|대박|싸다|위험/)
  })

  /** 과대약속 금지: 우리가 모르는 유형이 생겨도 숨기지 않는다. 근거를 그대로 보여준다. */
  it('모르는 유형은 payload를 그대로 내놓는다', () => {
    const line = reviewLine(item({ type: 'KEYWORD_SUGGEST', payload: { tokens: ['리퍼'] } }))

    expect(line.reason).toContain('KEYWORD_SUGGEST')
    expect(line.detail).toContain('리퍼')
  })

  /** payload는 jsonb다 — 기대한 필드가 없을 수 있다. 화면이 `undefined`를 그리면 안 된다. */
  it('제목이 없으면 undefined를 그리지 않는다', () => {
    const line = reviewLine(item({ type: 'UNCLASSIFIED', payload: { productCandidates: [1] } }))

    expect(line.detail).not.toContain('undefined')
    expect(line.detail).toContain('제목 없음')
  })

  it('가격이 숫자가 아니면 금액으로 꾸미지 않는다', () => {
    const line = reviewLine(item({ type: 'OUTLIER_LOWER', payload: { priceFirst: '칠십만' } }))

    expect(line.detail).not.toContain('원')
    expect(line.detail).toContain('칠십만')
  })
})
