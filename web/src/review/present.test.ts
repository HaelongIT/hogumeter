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
  subject: null,
  candidateProducts: [],
  conditions: [],
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

/** "700,000원"만 보고는 아무것도 결정할 수 없다. **무엇의** 이상치인지 말해야 한다. */
describe('reviewLine — 이상치의 대상', () => {
  it('대상을 제품 — variant로 말한다', () => {
    const line = reviewLine(
      item({ type: 'OUTLIER_LOWER', subject: '아이폰 17 — 256GB', payload: { priceFirst: 700000 } }),
    )

    expect(line.detail).toContain('아이폰 17 — 256GB')
    expect(line.detail).toContain('700,000원')
  })

  /** 딜이 미상이면 대상을 말할 수 없다. 지어내지 않고 그 사실을 말한다(과대약속 금지). */
  it('대상을 모르면 모른다고 말한다', () => {
    const line = reviewLine(item({ type: 'OUTLIER_LOWER', subject: null, payload: { priceFirst: 700000 } }))

    expect(line.detail).toContain('대상 미상')
    expect(line.detail).not.toContain('null')
    expect(line.detail).not.toContain('undefined')
  })
})


// ── 이상치는 **왜** 싸 보이는지 말해야 한다 ─────────────────────────────
//
// `700,000원`만 보고는 아무것도 결정할 수 없다. 그 가격이 `카할`(특정 카드 보유자만)이거나
// `배송비미상`(저장된 값이 하한)이면 그건 이상치가 아니라 **정상**이다.
// 조건 태그는 `deal_event.applied_conditions`까지 도달한다(Q-46 절반 해소) — 그린다.

describe('reviewLine — 조건 태그는 이상치의 이유다', () => {
  it('조건이 있으면 가격 옆에 이유를 붙인다', () => {
    const line = reviewLine(outlier({ conditions: ['배송비미상', '카할'] }))

    expect(line.detail).toContain('700,000원')
    expect(line.detail).toContain('조건부: 배송비미상 · 카할')
  })

  it('조건이 없으면 조건 문구를 지어내지 않는다', () => {
    const line = reviewLine(outlier({ conditions: [] }))

    expect(line.detail).not.toContain('조건부')
  })

  it('배송비미상이면 그 가격이 하한이라고 말한다 — 사람이 오판하지 않게', () => {
    const line = reviewLine(outlier({ conditions: ['배송비미상'] }))

    expect(line.detail).toContain('배송비 미상이라 실제 결제가는 더 높습니다')
  })

  it('미상 항목엔 조건 문구가 없다', () => {
    const line = reviewLine({ ...outlier({ conditions: [] }), type: 'UNCLASSIFIED', payload: { title: 'x' } })

    expect(line.detail).not.toContain('조건부')
  })
})

function outlier(over: Partial<ReviewQueueItem>): ReviewQueueItem {
  return {
    id: 1,
    type: 'OUTLIER_LOWER',
    occurrences: 1,
    firstSeenAt: '2026-07-10T00:00:00Z',
    lastSeenAt: '2026-07-10T00:00:00Z',
    sourceUrl: 'https://example.invalid/1',
    subject: '아이폰 17 — 256GB',
    candidateProducts: [],
    conditions: [],
    payload: { priceFirst: 700000 },
    ...over,
  }
}
