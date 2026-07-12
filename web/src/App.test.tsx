import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api/client'
import { App } from './App'

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
  })

  it('판단 화면으로 열린다 — 등록은 한 번 하고 마는 일이다', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: '지금 사도 되나' })).toBeInTheDocument()
  })

  it('탭으로 등록 화면에 간다', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByRole('heading', { name: '제품 등록' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '지금 사도 되나' })).not.toBeInTheDocument()
  })
})

describe('App — 등록에서 판단으로 이어진다', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
    vi.spyOn(api, 'registerProduct').mockResolvedValue({ productId: 9 })
    vi.spyOn(api, 'listVariants').mockResolvedValue([
      { variantId: 91, label: '256GB', priceAxisValues: { 용량: '256GB' } },
    ])
    vi.spyOn(api, 'listPurchases').mockResolvedValue([])
    vi.spyOn(api, 'getSignal').mockResolvedValue({ color: 'GRAY', goodDealLineEstablished: false, notes: [] })
    vi.spyOn(api, 'getBenchmark').mockResolvedValue({
      tier: 'NONE', benchmarkPrice: null, goodDealLine: null, periodLowest: null, latestDeal: null,
      n: 0, m: 0, expandedToMonths: null, currentPrice: null,
      gap: { vsBenchmark: null, vsLowest: null }, cases: [],
    })
    vi.spyOn(api, 'getCadence').mockResolvedValue({
      eventCount: 0, intervalMedianDays: null, elapsedDays: null, observedMonths: 6, guardMet: false,
    })
  })

  it('등록 후 variant를 고르면 판단 화면이 그 variant로 열린다', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: '제품 등록' }))
    await userEvent.type(await screen.findByLabelText('제품명'), '아이폰 17')
    await userEvent.type(screen.getByLabelText(/축 1 값/), '256GB')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))

    await userEvent.click(await screen.findByRole('button', { name: '256GB 판단 보기' }))

    expect(await screen.findByRole('heading', { name: '지금 사도 되나' })).toBeInTheDocument()
    // 사용자가 셀렉트를 다시 만지지 않아도 조회가 돈다.
    await waitFor(() => expect(api.getBenchmark).toHaveBeenCalledWith(91, 6))
  })
})
