import { describe, expect, it } from 'vitest'
import type { BenchmarkView, CadenceView, SignalView } from '../api/types'
import { benchmarkLine, cadenceLine, gapLine, sampleLabel, signalBadge } from './present'

/** 포맷된 금액(1,234,000원)이 문자열에 있는가. "표본이 빈약할 땐 숫자를 내지 않는다"를 검사한다. */
const PRICE_AMOUNT = /\d{1,3}(,\d{3})+\s*원/

const benchmark = (over: Partial<BenchmarkView> = {}): BenchmarkView => ({
  tier: 'SUFFICIENT',
  benchmarkPrice: 820_000,
  goodDealLine: 790_000,
  periodLowest: { price: 780_000, date: '2026-05-02' },
  latestDeal: { price: 799_000, date: '2026-07-01', site: 'ppomppu', sourceUrl: 'https://x' },
  n: 12,
  m: 3,
  expandedToMonths: null,
  currentPrice: 890_000,
  gap: { vsBenchmark: { won: 70_000, pct: 8.5 }, vsLowest: { won: 110_000, pct: 14.1 } },
  cases: [],
  ...over,
})

describe('sampleLabel — 기준가는 항상 표본을 동반한다 (절대 원칙 1)', () => {
  it('n건(교차 m건)', () => {
    expect(sampleLabel(12, 3)).toBe('12건(교차 3건)')
  })

  it('교차 0건도 감추지 않는다', () => {
    expect(sampleLabel(5, 0)).toBe('5건(교차 0건)')
  })
})

describe('benchmarkLine — tier가 표시를 지배한다', () => {
  it('SUFFICIENT: 기준가·굿딜라인·표본을 함께 낸다', () => {
    const line = benchmarkLine(benchmark())
    expect(line).toContain('820,000원')
    expect(line).toContain('790,000원')
    expect(line).toContain('12건(교차 3건)')
  })

  it('자동확장이 발동했으면 실효 개월을 밝힌다', () => {
    expect(benchmarkLine(benchmark({ expandedToMonths: 12 }))).toContain('12개월로 확장')
  })

  it('SPARSE: 통계 용어도 금액도 내지 않는다 — 사례만 있다고 말한다', () => {
    const line = benchmarkLine(
      benchmark({ tier: 'SPARSE', benchmarkPrice: null, goodDealLine: null, n: 4, m: 1 }),
    )
    expect(line).not.toMatch(PRICE_AMOUNT)
    expect(line).not.toMatch(/median|중앙값|굿딜라인/)
    expect(line).toContain('4건(교차 1건)')
    expect(line).toContain('산출하지 않')
  })

  it('NONE: 딜이 없다고만 말한다', () => {
    const line = benchmarkLine(benchmark({ tier: 'NONE', benchmarkPrice: null, goodDealLine: null, n: 0, m: 0 }))
    expect(line).not.toMatch(PRICE_AMOUNT)
    expect(line).toContain('수집된 딜이 없')
  })

  it('SUFFICIENT인데 기준가가 없으면 tier를 믿지 않고 없는 대로 말한다', () => {
    // 계약상 일어나면 안 되지만, 일어났을 때 "undefined원"을 그리지 않는다.
    const line = benchmarkLine(benchmark({ benchmarkPrice: null, goodDealLine: null }))
    expect(line).not.toContain('undefined')
    expect(line).not.toContain('NaN')
  })
})

describe('gapLine — 현재가 0은 "공짜"가 아니라 "미확립"이다', () => {
  it('현재가가 확립되면 비쌈/쌈을 원·%로 병기한다', () => {
    expect(gapLine(benchmark())).toBe('현재가 890,000원 — 기준가보다 70,000원 비쌈 (+8.5%)')
  })

  it('현재가가 기준가보다 싸면 "쌈"이다', () => {
    const view = benchmark({ currentPrice: 800_000, gap: { vsBenchmark: { won: -20_000, pct: -2.4 }, vsLowest: null } })
    expect(gapLine(view)).toBe('현재가 800,000원 — 기준가보다 20,000원 쌈 (-2.4%)')
  })

  it('currentPrice=0이면 갭을 계산해 주더라도 그리지 않는다 (StubCurrentPriceProvider)', () => {
    // core가 보내는 실제 모양: 현재가 0 → gap = 0 - 820,000 = -820,000원(-100.0%).
    // 이걸 그대로 그리면 "100% 싸다"는 거짓말이 된다.
    const view = benchmark({
      currentPrice: 0,
      gap: { vsBenchmark: { won: -820_000, pct: -100.0 }, vsLowest: { won: -780_000, pct: -100.0 } },
    })
    const line = gapLine(view)
    expect(line).not.toContain('820,000원')
    expect(line).not.toContain('100.0%')
    expect(line).not.toMatch(/쌈|비쌈/)
    expect(line).toContain('현재가 미확립')
  })

  it('참조가(기준가)가 없으면 갭도 없다 — 없음을 없다고 말한다', () => {
    const view = benchmark({ tier: 'SPARSE', benchmarkPrice: null, gap: { vsBenchmark: null, vsLowest: null } })
    expect(gapLine(view)).toContain('비교할 기준가가 없')
  })
})

describe('cadenceLine — 발생·간격·경과일만. 예측하지 않는다', () => {
  const cadence = (over: Partial<CadenceView> = {}): CadenceView => ({
    eventCount: 6,
    intervalMedianDays: 28,
    elapsedDays: 9,
    observedMonths: 6,
    guardMet: true,
    ...over,
  })

  it('가드 통과: 발생 수·간격 median·경과일', () => {
    const line = cadenceLine(cadence())
    expect(line).toContain('6개월 6회')
    expect(line).toContain('간격 median 28일')
    expect(line).toContain('마지막 딜 9일 전')
  })

  it('어떤 경우에도 "예상"·"예측"을 말하지 않는다', () => {
    const lines = [cadence(), cadence({ guardMet: false, eventCount: 2, intervalMedianDays: null }), cadence({ eventCount: 0, intervalMedianDays: null, elapsedDays: null, guardMet: false })]
    for (const c of lines) {
      expect(cadenceLine(c)).not.toMatch(/예상|예측|다음 딜은/)
    }
  })

  it('가드 미달: 간격을 내지 않고 판단 불가라고 말한다', () => {
    const line = cadenceLine(cadence({ guardMet: false, eventCount: 2, intervalMedianDays: null }))
    expect(line).not.toContain('median')
    expect(line).toContain('주기 판단 불가')
    expect(line).toContain('2회')
  })

  it('발생 0회: 경과일도 없다', () => {
    const line = cadenceLine(cadence({ eventCount: 0, intervalMedianDays: null, elapsedDays: null, guardMet: false }))
    expect(line).toContain('발생 없음')
    expect(line).not.toContain('null')
  })
})

describe('signalBadge — 색은 표시 전용이고 딱지를 감추지 않는다', () => {
  const signal = (over: Partial<SignalView> = {}): SignalView => ({
    color: 'GREEN',
    goodDealLineEstablished: true,
    notes: [],
    ...over,
  })

  it('GREEN', () => {
    expect(signalBadge(signal())).toMatchObject({ mark: '🟢', text: '지금 잡을 딜 있음' })
  })

  it('GRAY는 판단이 아니라 보류다', () => {
    const badge = signalBadge(signal({ color: 'GRAY', goodDealLineEstablished: false, notes: ['굿딜라인 미확립'] }))
    expect(badge.mark).toBe('⚪')
    expect(badge.text).toContain('판단 보류')
    expect(badge.notes).toEqual(['굿딜라인 미확립'])
  })

  it('노란불·빨간불도 각자의 뜻을 갖는다', () => {
    expect(signalBadge(signal({ color: 'YELLOW' })).mark).toBe('🟡')
    expect(signalBadge(signal({ color: 'RED' })).mark).toBe('🔴')
  })

  it('굿딜라인이 미확립이면 초록이라도 그 사실을 함께 낸다', () => {
    const badge = signalBadge(signal({ color: 'GREEN', goodDealLineEstablished: false, notes: [] }))
    expect(badge.notes).toContain('굿딜라인 미확립')
  })
})
