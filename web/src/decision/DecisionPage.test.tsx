import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { BenchmarkView, CadenceView, CoupangLatestPrice, SignalView } from '../api/types'
import { DecisionPage } from './DecisionPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: 'phone',
  demandAxisMode: 'GROUPED' as const,
  axes: [{ axisType: 'PRICE' as const, name: '용량', allowedValues: ['256GB', '512GB'] }],
  variants: [{ variantId: 11, label: '256GB', priceAxisValues: { 용량: '256GB' } }],
}

const signal: SignalView = { color: 'GREEN', goodDealLineEstablished: true, notes: [] }

const cadence: CadenceView = {
  eventCount: 6,
  intervalMedianDays: 28,
  elapsedDays: 9,
  observedMonths: 6,
  guardMet: true,
}

const benchmark: BenchmarkView = {
  tier: 'SUFFICIENT',
  benchmarkPrice: 820_000,
  goodDealLine: 790_000,
  periodLowest: { price: 780_000, date: '2026-05-02' },
  latestDeal: { price: 799_000, date: '2026-07-01', site: 'ppomppu', sourceUrl: 'https://ppomppu/1', conditions: [] },
  n: 12,
  m: 3,
  expandedToMonths: null,
  currentPrice: 890_000,
  gap: { vsBenchmark: { won: 70_000, pct: 8.5 }, vsLowest: { won: 110_000, pct: 14.1 } },
  cases: [],
  outliers: [],
}

/** CMP-01 — 확장이 아직 없는 대부분의 테스트는 미연동(전 필드 null) 모양을 쓴다. */
const coupangUnavailable: CoupangLatestPrice = {
  regularPrice: null,
  wowPrice: null,
  shippingFee: null,
  url: null,
  observedAt: null,
}

const pick = () => userEvent.selectOptions(screen.getByLabelText('variant'), '11')

describe('DecisionPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'getSignal').mockResolvedValue(signal)
    vi.spyOn(api, 'getBenchmark').mockResolvedValue(benchmark)
    vi.spyOn(api, 'getCadence').mockResolvedValue(cadence)
    vi.spyOn(api, 'getCoupangLatestPrice').mockResolvedValue(coupangUnavailable)
    vi.spyOn(api, 'listPurchases').mockResolvedValue([]) // 패널이 같은 화면에 있다
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, excludeKeywords: [] }) // 알림 정책 패널도
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
  })

  it('variant를 고르면 신호등·기준가·갭·주기를 한 화면에 낸다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    // 판정 히어로: 답(헤드라인) + 그 아래 결정적 한 줄. 서브라인이 빠지면 답만 있고 근거가 사라진다.
    const verdict = await screen.findByLabelText('신호등')
    expect(verdict).toHaveTextContent('지금 잡을 딜 있음')
    expect(verdict).toHaveTextContent('기준가보다 8.5% 비쌈 · 현재가 890,000원')

    expect(screen.getByLabelText('기준가')).toHaveTextContent('핫딜 기준가 820,000원')
    expect(screen.getByLabelText('기준가')).toHaveTextContent('12건(교차 3건)')
    expect(screen.getByLabelText('갭')).toHaveTextContent('70,000원 비쌈')
    // "기준가보다 비싸다"만으로는 기다릴지 말지 못 정한다 — 이 기간에 얼마까지 내려갔었나.
    expect(screen.getByLabelText('기간 최저')).toHaveTextContent('기간 최저 780,000원 (2026-05-02)')
    expect(screen.getByLabelText('딜 주기')).toHaveTextContent('간격 median 28일')
    expect(screen.getByRole('link', { name: '원문' })).toHaveAttribute('href', 'https://ppomppu/1')
    // CMP-01 — 확장 미연동이면 미확인이라고 말하고 금액을 지어내지 않는다.
    expect(screen.getByLabelText('쿠팡 관측가')).toHaveTextContent('미확인')
  })

  it('쿠팡 확장이 관측을 보냈으면 정가·와우가를 함께 낸다 (CMP-01)', async () => {
    vi.spyOn(api, 'getCoupangLatestPrice').mockResolvedValue({
      regularPrice: 899_000,
      wowPrice: 849_000,
      shippingFee: 0,
      url: 'https://coupang.test/1',
      observedAt: '2026-07-20T00:00:00Z',
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByLabelText('쿠팡 관측가')).toHaveTextContent('쿠팡 정가 899,000원')
    expect(screen.getByLabelText('쿠팡 관측가')).toHaveTextContent('와우가 849,000원')
  })

  it('현재가 미확립(null)이면 갭 자리에 거짓말 대신 이유를 쓴다', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      ...benchmark,
      currentPrice: null,
      gap: { vsBenchmark: { won: -820_000, pct: -100 }, vsLowest: { won: -780_000, pct: -100 } },
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    const gap = await screen.findByLabelText('갭')
    expect(gap).toHaveTextContent('현재가 미확립')
    expect(gap).not.toHaveTextContent('쌈')
  })

  it('SPARSE면 기준가 대신 사례를 원문 링크와 함께 보여준다 (판단은 사람)', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      ...benchmark,
      tier: 'SPARSE',
      benchmarkPrice: null,
      goodDealLine: null,
      n: 2,
      m: 0,
      gap: { vsBenchmark: null, vsLowest: null },
      cases: [
        { price: 810_000, date: '2026-06-01', site: 'ruliweb', sourceUrl: 'https://r/1', conditions: ['카할'] },
        { price: 830_000, date: '2026-04-11', site: 'fmkorea', sourceUrl: 'https://f/2', conditions: [] },
      ],
      latestDeal: null,
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByLabelText('기준가')).toHaveTextContent('산출하지 않습니다')
    expect(screen.getByRole('heading', { name: '사례 2건' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '원문' })).toHaveLength(2)
    // 조건부 사례는 "조건부: 카할"을 병기한다 — 정상 가격으로 오인 방지(Q-46①).
    expect(screen.getByText(/조건부: 카할/)).toBeInTheDocument()
  })

  it('GRAY는 판단 보류이고 딱지를 함께 낸다', async () => {
    vi.spyOn(api, 'getSignal').mockResolvedValue({
      color: 'GRAY',
      goodDealLineEstablished: false,
      notes: ['굿딜라인 미확립'],
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByLabelText('신호등')).toHaveTextContent('판단 보류')
    expect(screen.getByLabelText('딱지')).toHaveTextContent('굿딜라인 미확립')
  })

  it('셋 중 하나라도 실패하면 반쪽 화면 대신 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'getCadence').mockRejectedValue(new ApiFailure(404, 'BM_VARIANT_NOT_FOUND'))
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByRole('alert')).toHaveTextContent('조회 실패 (BM_VARIANT_NOT_FOUND)')
    expect(screen.queryByLabelText('판단 요약')).not.toBeInTheDocument()
  })

  it('등록된 variant가 없으면 조회를 시도하지 않는다', async () => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
    render(<DecisionPage />)

    expect(await screen.findByText(/등록된 variant가 없습니다/)).toBeInTheDocument()
    await waitFor(() => expect(api.getSignal).not.toHaveBeenCalled())
  })
})

/**
 * Q-66 ①: 분리(SPLIT) 제품은 **값마다 분포가 다르다**. 값을 고르기 전에 조회하면 core가 400을 내고,
 * 전체로 답하게 두면 그건 묶음이다 — 그래서 고를 때까지 묻지 않고, 고르면 그 값으로 묻는다.
 */
describe('DecisionPage — 수요축 분리 제품', () => {
  const galaxy = {
    productId: 2,
    name: '갤럭시 25',
    category: 'phone',
    demandAxisMode: 'SPLIT' as const,
    axes: [
      { axisType: 'PRICE' as const, name: '용량', allowedValues: ['256GB'] },
      { axisType: 'DEMAND' as const, name: '색상', allowedValues: ['블랙', '화이트'] },
    ],
    variants: [{ variantId: 21, label: '256GB', priceAxisValues: { 용량: '256GB' } }],
  }

  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([galaxy])
    vi.spyOn(api, 'getSignal').mockResolvedValue(signal)
    vi.spyOn(api, 'getBenchmark').mockResolvedValue(benchmark)
    vi.spyOn(api, 'getCadence').mockResolvedValue(cadence)
    vi.spyOn(api, 'getCoupangLatestPrice').mockResolvedValue(coupangUnavailable)
    vi.spyOn(api, 'listPurchases').mockResolvedValue([])
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
  })

  it('값을 고르기 전엔 묻지 않고, 왜 골라야 하는지 말한다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '갤럭시 25 — 256GB' })
    await userEvent.selectOptions(screen.getByLabelText('variant'), '21')

    expect(await screen.findByRole('note', { name: '수요축 안내' })).toHaveTextContent('색상')
    await waitFor(() => expect(api.getBenchmark).not.toHaveBeenCalled())
  })

  it('값을 고르면 신호등·기준가를 **같은 값**으로 부른다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '갤럭시 25 — 256GB' })
    await userEvent.selectOptions(screen.getByLabelText('variant'), '21')
    await userEvent.selectOptions(await screen.findByLabelText('색상'), '블랙')

    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(21, 6, '블랙', false))
    expect(api.getSignal).toHaveBeenCalledWith(21, '블랙')
  })
})

describe('DecisionPage — 기간 손잡이 (원칙 4)', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'getSignal').mockResolvedValue(signal)
    vi.spyOn(api, 'getBenchmark').mockResolvedValue(benchmark)
    vi.spyOn(api, 'getCadence').mockResolvedValue(cadence)
    vi.spyOn(api, 'getCoupangLatestPrice').mockResolvedValue(coupangUnavailable)
    vi.spyOn(api, 'listPurchases').mockResolvedValue([]) // 패널이 같은 화면에 있다
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, excludeKeywords: [] }) // 알림 정책 패널도
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
  })

  it('기본은 6개월이고, 기준가·주기를 그 기간으로 부른다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(11, 6, null, false))
    expect(api.getCadence).toHaveBeenCalledWith(11, 6)
  })

  it('기간을 바꾸면 그 기간으로 다시 부른다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()
    await userEvent.selectOptions(screen.getByLabelText('기간'), '12')

    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(11, 12, null, false))
    expect(api.getCadence).toHaveBeenCalledWith(11, 12)
  })

  it('신호등이 기간을 따르지 않는다는 사실을 숨기지 않는다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(screen.queryByRole('note', { name: '신호등 기간 안내' })).not.toBeInTheDocument() // 6개월이면 군더더기 없음
    await userEvent.selectOptions(screen.getByLabelText('기간'), '3')

    expect(await screen.findByRole('note', { name: '신호등 기간 안내' })).toHaveTextContent(
      '신호등은 기간 설정과 무관하게 최근 6개월',
    )
    // core는 신호등에 기간을 받지 않는다 — 인자를 지어내지 않았다.
    expect(api.getSignal).toHaveBeenLastCalledWith(11, null)
  })
})

describe('DecisionPage — 이상치 토글 (Q-11, 기본 숨김)', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'getSignal').mockResolvedValue(signal)
    vi.spyOn(api, 'getCadence').mockResolvedValue(cadence)
    vi.spyOn(api, 'getCoupangLatestPrice').mockResolvedValue(coupangUnavailable)
    vi.spyOn(api, 'listPurchases').mockResolvedValue([])
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, excludeKeywords: [] })
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
  })

  it('기본은 꺼져 있고, 켜면 includeOutliers=true로 다시 부른다', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue(benchmark)
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(11, 6, null, false))

    await userEvent.click(screen.getByLabelText('이상치 포함(참고용)'))

    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(11, 6, null, true))
  })

  it('토글을 켜고 이상치가 있으면 계산 진실과 분리해 보여준다', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      ...benchmark,
      outliers: [
        { price: 5_000_000, date: '2026-07-02', site: 'ppomppu', sourceUrl: 'https://p/9', conditions: [] },
      ],
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()
    await userEvent.click(screen.getByLabelText('이상치 포함(참고용)'))

    expect(await screen.findByRole('heading', { name: /이상치 1건/ })).toBeInTheDocument()
    expect(screen.getByLabelText('이상치')).toHaveTextContent('5,000,000원')
    expect(screen.getByLabelText('이상치')).toHaveTextContent('기준가 계산에서 제외됨')
  })

  it('토글이 꺼져 있으면 outliers가 응답에 있어도 그리지 않는다', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      ...benchmark,
      outliers: [
        { price: 5_000_000, date: '2026-07-02', site: 'ppomppu', sourceUrl: 'https://p/9', conditions: [] },
      ],
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    await screen.findByLabelText('판단 요약')
    expect(screen.queryByLabelText('이상치')).not.toBeInTheDocument()
  })
})
