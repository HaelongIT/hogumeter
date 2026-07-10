import { describe, expect, it } from 'vitest'
import type { ReviewQueueItem } from '../api/types'
import { reviewLine, seenLine } from './present'

const item = (over: Partial<ReviewQueueItem>): ReviewQueueItem => ({
  id: 1,
  type: 'UNCLASSIFIED',
  occurrences: 1,
  firstSeenAt: '2026-07-10T00:00:00Z',
  lastSeenAt: '2026-07-10T00:00:00Z',
  sourceUrl: null,
  candidateProducts: [],
  payload: {},
  ...over,
})

describe('reviewLine', () => {
  it('미상은 무엇을 못 했는지 말한다 — 제목과 후보 제품', () => {
    const line = reviewLine(
      item({
        type: 'UNCLASSIFIED',
        payload: { title: '아이폰17 특가', productCandidates: [3, 7] },
        candidateProducts: ['아이폰 17', '아이폰 17 프로'],
      }),
    )

    expect(line.reason).toContain('어느 제품인지 확정하지 못했습니다')
    expect(line.detail).toContain('아이폰17 특가')
    // id(3, 7)는 사람이 읽는 값이 아니다. core가 이름으로 풀어 준다.
    expect(line.detail).toContain('후보: 아이폰 17, 아이폰 17 프로')
  })

  it('후보가 하나도 없으면 그렇게 말한다 — 0을 숨기지 않는다', () => {
    const line = reviewLine(item({ payload: { title: '뭔가' }, candidateProducts: [] }))

    expect(line.detail).toContain('후보 없음')
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

/**
 * `firstSeenAt`·`lastSeenAt`은 core가 내는데 **화면이 아무도 읽지 않았다** — 우리가 만든 죽은 필드다.
 * `occurrences`만 보면 "47번 쌓였다"는 알지만 "언제부터"를 모른다.
 */
describe('seenLine', () => {
  it('한 번만 쌓였으면 접수 날짜만 말한다', () => {
    const line = seenLine(item({ occurrences: 1, firstSeenAt: '2026-07-10T00:00:00Z', lastSeenAt: '2026-07-10T00:00:00Z' }))

    expect(line).toBe('2026-07-10 접수')
    expect(line).not.toContain('다시 쌓였습니다')
  })

  it('같은 날 여러 번이면 날짜를 두 번 쓰지 않는다', () => {
    const line = seenLine(item({ occurrences: 47, firstSeenAt: '2026-07-10T01:00:00Z', lastSeenAt: '2026-07-10T05:00:00Z' }))

    expect(line).toContain('2026-07-10 ·')
    expect(line).toContain('47번 다시 쌓였습니다')
    expect(line).not.toContain('~')
  })

  it('여러 날에 걸쳤으면 구간을 말한다 — "언제부터"가 곧 결함의 나이다', () => {
    const line = seenLine(item({ occurrences: 1440, firstSeenAt: '2026-07-08T00:00:00Z', lastSeenAt: '2026-07-10T00:00:00Z' }))

    expect(line).toContain('2026-07-08 ~ 2026-07-10')
    expect(line).toContain('1440번')
  })

  /** 시각은 KST로 읽는다 — UTC 15:30은 이미 다음 날이다(`purchase/present.ts`의 규약을 그대로 쓴다). */
  it('KST 경계를 넘는 시각은 다음 날로 읽는다', () => {
    const line = seenLine(item({ occurrences: 1, firstSeenAt: '2026-07-10T15:30:00Z', lastSeenAt: '2026-07-10T15:30:00Z' }))

    expect(line).toBe('2026-07-11 접수')
  })
})

/** "후보 2개"는 판단에 아무 도움이 안 된다. **무엇의 후보인지**를 말해야 사람이 1초 만에 고른다. */
describe('reviewLine — 후보 제품', () => {
  it('후보를 이름으로 말한다', () => {
    const line = reviewLine(item({ payload: { title: 'x' }, candidateProducts: ['아이폰 17', '갤럭시 S26'] }))

    expect(line.detail).toContain('후보: 아이폰 17, 갤럭시 S26')
    expect(line.detail).not.toContain('후보 2개')
  })

  it('후보가 없으면 그 사실을 말한다 — 0을 숨기지 않는다', () => {
    const line = reviewLine(item({ payload: { title: 'x' }, candidateProducts: [] }))

    expect(line.detail).toContain('후보 없음')
  })

  /** core가 `#999`로 보내면 그대로 그린다. 사라진 제품을 숨기면 근거가 줄어든 걸 아무도 모른다. */
  it('사라진 제품은 #id로 그대로 그린다', () => {
    const line = reviewLine(item({ payload: { title: 'x' }, candidateProducts: ['#999'] }))

    expect(line.detail).toContain('#999')
  })
})
