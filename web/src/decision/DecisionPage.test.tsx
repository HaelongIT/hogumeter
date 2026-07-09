import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { BenchmarkView, CadenceView, SignalView } from '../api/types'
import { DecisionPage } from './DecisionPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: 'phone',
  demandAxisMode: 'GROUPED' as const,
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
  latestDeal: { price: 799_000, date: '2026-07-01', site: 'ppomppu', sourceUrl: 'https://ppomppu/1' },
  n: 12,
  m: 3,
  expandedToMonths: null,
  currentPrice: 890_000,
  gap: { vsBenchmark: { won: 70_000, pct: 8.5 }, vsLowest: { won: 110_000, pct: 14.1 } },
  cases: [],
}

const pick = () => userEvent.selectOptions(screen.getByLabelText('variant'), '11')

describe('DecisionPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'getSignal').mockResolvedValue(signal)
    vi.spyOn(api, 'getBenchmark').mockResolvedValue(benchmark)
    vi.spyOn(api, 'getCadence').mockResolvedValue(cadence)
  })

  it('variant를 고르면 신호등·기준가·갭·주기를 한 화면에 낸다', async () => {
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByLabelText('신호등')).toHaveTextContent('지금 잡을 딜 있음')
    expect(screen.getByLabelText('기준가')).toHaveTextContent('핫딜 기준가 820,000원')
    expect(screen.getByLabelText('기준가')).toHaveTextContent('12건(교차 3건)')
    expect(screen.getByLabelText('갭')).toHaveTextContent('70,000원 비쌈')
    expect(screen.getByLabelText('딜 주기')).toHaveTextContent('간격 median 28일')
    expect(screen.getByRole('link', { name: '원문' })).toHaveAttribute('href', 'https://ppomppu/1')
  })

  it('현재가 미확립(0)이면 갭 자리에 거짓말 대신 이유를 쓴다', async () => {
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      ...benchmark,
      currentPrice: 0,
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
        { price: 810_000, date: '2026-06-01', site: 'ruliweb', sourceUrl: 'https://r/1' },
        { price: 830_000, date: '2026-04-11', site: 'fmkorea', sourceUrl: 'https://f/2' },
      ],
      latestDeal: null,
    })
    render(<DecisionPage />)
    await screen.findByRole('option', { name: '아이폰 17 — 256GB' })
    await pick()

    expect(await screen.findByLabelText('기준가')).toHaveTextContent('산출하지 않습니다')
    expect(screen.getByRole('heading', { name: '사례 2건' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: '원문' })).toHaveLength(2)
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
