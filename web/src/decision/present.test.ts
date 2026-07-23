import { describe, expect, it } from 'vitest'
import type { BenchmarkView, CadenceView, SignalView } from '../api/types'
import {
  benchmarkLine,
  cadenceLine,
  conditionsSuffix,
  gapLine,
  lowestLine,
  sampleLabel,
  signalBadge,
  verdictSubline,
} from './present'

/** 포맷된 금액(1,234,000원)이 문자열에 있는가. "표본이 빈약할 땐 숫자를 내지 않는다"를 검사한다. */
const PRICE_AMOUNT = /\d{1,3}(,\d{3})+\s*원/

const benchmark = (over: Partial<BenchmarkView> = {}): BenchmarkView => ({
  tier: 'SUFFICIENT',
  benchmarkPrice: 820_000,
  goodDealLine: 790_000,
  periodLowest: { price: 780_000, date: '2026-05-02' },
  latestDeal: { price: 799_000, date: '2026-07-01', site: 'ppomppu', sourceUrl: 'https://x', conditions: [] },
  n: 12,
  m: 3,
  expandedToMonths: null,
  currentPrice: 890_000,
  gap: { vsBenchmark: { won: 70_000, pct: 8.5 }, vsLowest: { won: 110_000, pct: 14.1 } },
  cases: [],
  outliers: [],
  ...over,
})

describe('conditionsSuffix — 조건부 사례는 "정상 가격"으로 오인시키지 않는다 (Q-46①)', () => {
  it('조건이 없으면 빈 문자열', () => {
    expect(conditionsSuffix([])).toBe('')
  })

  it('조건이 있으면 · 조건부로 병기한다', () => {
    expect(conditionsSuffix(['카할'])).toBe(' · 조건부: 카할')
    expect(conditionsSuffix(['카할', '조건부무료배송'])).toBe(' · 조건부: 카할 · 조건부무료배송')
  })
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

  it('currentPrice=null이면 갭 값이 실려 와도 그리지 않는다 (미확립 우선, Q-53)', () => {
    // core는 미확립이면 currentPrice=null·gap의 두 leg도 null로 보낸다. 여기선 방어적으로
    // 갭 값을 실어 두어도 현재가 null이 우선함을 못박는다 — 혹시라도 "100% 싸다"로 새지 않는다.
    const view = benchmark({
      currentPrice: null,
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
    expect(signalBadge(signal())).toMatchObject({ text: '지금 잡을 딜 있음' })
  })

  it('GRAY는 판단이 아니라 보류다', () => {
    const badge = signalBadge(signal({ color: 'GRAY', goodDealLineEstablished: false, notes: ['굿딜라인 미확립'] }))
    expect(badge.text).toContain('판단 보류')
    expect(badge.notes).toEqual(['굿딜라인 미확립'])
  })

  it('노란불·빨간불도 각자의 뜻을 갖는다', () => {
    expect(signalBadge(signal({ color: 'YELLOW' })).text).toBe('기준가 아래 딜 있음')
    expect(signalBadge(signal({ color: 'RED' })).text).toBe('지금은 잡을 딜 없음')
  })

  it('굿딜라인이 미확립이면 초록이라도 그 사실을 함께 낸다', () => {
    const badge = signalBadge(signal({ color: 'GREEN', goodDealLineEstablished: false, notes: [] }))
    expect(badge.notes).toContain('굿딜라인 미확립')
  })
})

/**
 * 히어로의 결정적 한 줄. 답(헤드라인) 바로 아래라 **가장 크게 읽히는 근거**다 — 여기서 거짓말하면
 * 가장 크게 거짓말하는 셈이라, 기준가가 설 때만 숫자를 낸다.
 */
describe('verdictSubline — 답 아래 한 줄. 없는 근거는 지어내지 않는다', () => {
  it('기준가가 서면 결정적 %와 현재가를 든다', () => {
    expect(verdictSubline(benchmark())).toBe('기준가보다 8.5% 비쌈 · 현재가 890,000원')
  })

  it('현재가보다 기준가가 높으면 "쌈"이고, 부호가 아니라 말로 방향을 낸다', () => {
    const view = benchmark({ currentPrice: 750_000, gap: { vsBenchmark: { won: -70_000, pct: -8.5 }, vsLowest: null } })
    expect(verdictSubline(view)).toBe('기준가보다 8.5% 쌈 · 현재가 750,000원')
  })

  it('현재가 미확립(Q-53)이면 갭을 말하지 않는다', () => {
    const view = benchmark({ currentPrice: null, gap: { vsBenchmark: null, vsLowest: null } })
    const line = verdictSubline(view)
    expect(line).toContain('현재가 미확립')
    expect(line).not.toMatch(PRICE_AMOUNT)
  })

  it('SPARSE면 비교할 기준가가 없다고 말하고 **금액을 내지 않는다**', () => {
    const view = benchmark({ tier: 'SPARSE', benchmarkPrice: null, n: 2, gap: { vsBenchmark: null, vsLowest: null } })
    const line = verdictSubline(view)
    expect(line).toContain('비교할 기준가가 아직 없습니다')
    expect(line).not.toMatch(PRICE_AMOUNT)
  })

  it('현재가가 기준가와 같으면 그렇게 말한다', () => {
    const view = benchmark({ currentPrice: 820_000, gap: { vsBenchmark: { won: 0, pct: 0 }, vsLowest: null } })
    expect(verdictSubline(view)).toBe('현재가가 기준가와 같습니다')
  })
})

/**
 * `periodLowest`(기간 최저가·날짜)와 `gap.vsLowest`는 core가 내는데 **화면이 아무도 읽지 않았다.**
 * "기준가보다 비싸다"만으로는 기다릴지 말지 못 정한다 — **이 기간 최저가 언제 얼마였나**가 근거다.
 */
describe('lowestLine — 기간 최저가', () => {
  const withLowest = (over: Partial<BenchmarkView> = {}): BenchmarkView => ({
    tier: 'SUFFICIENT',
    benchmarkPrice: 820_000,
    goodDealLine: 790_000,
    periodLowest: { price: 780_000, date: '2026-05-02' },
    latestDeal: null,
    n: 12,
    m: 3,
    expandedToMonths: null,
    currentPrice: 890_000,
    gap: { vsBenchmark: { won: 70_000, pct: 8.5 }, vsLowest: { won: 110_000, pct: 14.1 } },
    cases: [],
    outliers: [],
    ...over,
  })

  it('최저가와 그 날짜를 말하고, 현재가가 그보다 얼마나 비싼지 말한다', () => {
    const line = lowestLine(withLowest())

    expect(line).toContain('기간 최저 780,000원')
    expect(line).toContain('2026-05-02')
    expect(line).toContain('110,000원 비쌈')
    expect(line).toContain('+14.1%')
  })

  /** 현재가 미확립(null)이면 갭을 그리지 않는다 — 최저가 자체는 관측된 사실이라 그대로 말한다. */
  it('현재가 미확립이면 최저가만 말하고 갭은 그리지 않는다', () => {
    const line = lowestLine(withLowest({ currentPrice: null, gap: { vsBenchmark: null, vsLowest: null } }))

    expect(line).toContain('기간 최저 780,000원')
    expect(line).not.toContain('비쌈')
    expect(line).not.toContain('%')
  })

  it('현재가가 기간 최저와 같으면 그렇게 말한다', () => {
    const line = lowestLine(withLowest({ currentPrice: 780_000, gap: { vsBenchmark: null, vsLowest: { won: 0, pct: 0 } } }))

    expect(line).toContain('기간 최저와 같습니다')
    expect(line).not.toContain('비쌈')
  })

  /** 관측된 최저가가 없으면 줄 자체를 그리지 않는다 — "0원"이나 "최저 없음"을 지어내지 않는다. */
  it('기간 최저가 없으면 null이다', () => {
    expect(lowestLine(withLowest({ periodLowest: null }))).toBeNull()
  })
})
