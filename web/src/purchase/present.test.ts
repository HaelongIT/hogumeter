import { describe, expect, it } from 'vitest'
import type { PurchaseObservation, ReportCard } from '../api/types'
import { kstDate, observationLine, reportCardLine, stateLabel } from './present'

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
  reportCard: null,
  ...over,
})

const card = (over: Partial<ReportCard> = {}): ReportCard => ({
  unobserved: false,
  n: 3,
  cheaperCount: 2,
  percentile: 0.667,
  lowestOpportunity: 840_000,
  paidPrice: 899_000,
  paidGap: 79_000,
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

  /**
   * "집계 중"은 **진행 중**이라는 뜻이다. 실제로는 성적표를 발급하는 코드가 없어(docs/91 Q-62)
   * 구매는 여기서 영원히 멈춘다. 기다리면 나온다고 믿게 두는 것이 과대약속이다(절대 원칙 6).
   */
  it('REPORT_PENDING: 아직 없는 성적을 지어내지 않고, 기다리면 나온다고도 하지 않는다', () => {
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
    expect(line).toContain('관찰 종료')
    expect(line).toContain('성적표는 아직 발급되지 않습니다')
    expect(line).not.toContain('집계 중')
  })
})

describe('reportCardLine — 발급된 성적표. 짓지 않고, "호구" 등급을 매기지 않는다', () => {
  it('정상: n건 중 더 싼 건수 · 기준가 갭 · 최저 기회를 raw로 말한다(등급 라벨 없음)', () => {
    const line = reportCardLine(card())
    expect(line).toContain('3건 중 2건이 내 구매가보다 쌌습니다')
    expect(line).toContain('기준가보다 79,000원 비쌈') // paidGap 양수 = 비쌈
    expect(line).toContain('기간 내 최저 840,000원')
    expect(line).not.toMatch(/호구|잘 샀|등급/) // 판단은 사람(절대 원칙 2)
  })

  it('내가 기준가보다 쌌으면 "쌈"이라 말한다 (음수 갭을 "비쌈"이라 하지 않는다)', () => {
    expect(reportCardLine(card({ paidGap: -30_000 }))).toContain('기준가보다 30,000원 쌈')
  })

  it('기준가가 없으면(paidGap null) 갭을 그리지 않는다', () => {
    const line = reportCardLine(card({ paidGap: null }))
    expect(line).toContain('3건 중 2건')
    expect(line).not.toContain('기준가')
  })

  it('UNOBSERVED(관측 시작 이전 구매): 통계를 짓지 않는다 — 금액 문구가 없다', () => {
    const line = reportCardLine(card({ unobserved: true, n: 0, cheaperCount: 0, percentile: null, lowestOpportunity: null, paidGap: null }))
    expect(line).toContain('관측 시작 이전')
    expect(line).not.toMatch(/\d{1,3}(,\d{3})+\s*원/) // SPARSE/UNOBSERVED엔 금액 없음(절대 원칙 1)
  })

  it('n=0(관찰 기간에 비교 딜 없음): 통계를 짓지 않는다 — 금액 문구가 없다', () => {
    const line = reportCardLine(card({ n: 0, cheaperCount: 0, percentile: null, lowestOpportunity: null, paidGap: null }))
    expect(line).toContain('비교할 딜이 없었습니다')
    expect(line).not.toMatch(/\d{1,3}(,\d{3})+\s*원/)
  })
})

describe('stateLabel', () => {
  it.each([
    ['OBSERVING', '관찰 중'],
    ['REPORT_PENDING', '관찰 종료(성적표 미발급)'],
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
