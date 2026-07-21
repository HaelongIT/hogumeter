import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { PurchaseObservation } from '../api/types'
import { PurchasePanel } from './PurchasePanel'

const observing: PurchaseObservation = {
  purchaseId: 7,
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
}

const closed: PurchaseObservation = {
  purchaseId: 9,
  state: 'CLOSED',
  paidPrice: 899_000,
  purchasedAt: '2026-07-01T14:59:00Z',
  context: { mode: 'REPORT_PENDING', activeLowestPriceLast: null, overpaidWon: null, overpaidPct: null, observationDay: null, cheaperChanceCount: null },
  reportCard: { unobserved: false, n: 3, cheaperCount: 2, percentile: 0.667, lowestOpportunity: 840_000, paidPrice: 899_000, paidGap: 79_000 },
}

const fill = async () => {
  await userEvent.type(screen.getByLabelText(/실지불가/), '899,000')
  await userEvent.type(screen.getByLabelText('구매일'), '2026-07-01')
}

describe('PurchasePanel', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listPurchases').mockResolvedValue([])
    vi.spyOn(api, 'recordPurchase').mockResolvedValue({ purchaseId: 7 })
  })

  it('기록이 없으면 없다고 말한다', async () => {
    render(<PurchasePanel variantId={11} />)
    expect(await screen.findByText(/구매 기록이 없습니다/)).toBeInTheDocument()
  })

  it('폼을 채워 기록하면 23:59 KST로 환산해 보내고 목록을 다시 부른다', async () => {
    render(<PurchasePanel variantId={11} />)
    await fill()
    await userEvent.click(screen.getByRole('button', { name: '기록' }))

    await waitFor(() =>
      expect(api.recordPurchase).toHaveBeenCalledWith({
        variantId: 11,
        paidPrice: 899_000,
        purchasedAt: '2026-07-01T14:59:00.000Z',
        observationDays: null,
        demandAxisValue: null,
        linkedDealEventId: null,
      }),
    )
    expect(api.listPurchases).toHaveBeenCalledTimes(2) // 초기 + 기록 후
  })

  /**
   * Q-66 ③: 분리 제품이면 판단 화면에서 고른 값을 그대로 기록에 싣는다 — 자유 입력을 다시 받으면 판단과
   * 다른 색을 적을 수 있고, core는 400을 낸다. 그래서 입력이 아니라 <b>안내</b>로 보여준다.
   */
  it('분리 제품이면 고른 수요축 값으로 기록하고, 다시 입력받지 않는다', async () => {
    render(<PurchasePanel variantId={11} demandAxisValue="블랙" />)
    await fill()
    await userEvent.click(screen.getByRole('button', { name: '기록' }))

    await waitFor(() =>
      expect(api.recordPurchase).toHaveBeenCalledWith(expect.objectContaining({ demandAxisValue: '블랙' })),
    )
    // 자유 입력 필드는 없다 — 판단 화면의 선택이 유일한 출처다.
    expect(screen.queryByRole('textbox', { name: /수요축/ })).toBeNull()
    expect(screen.getByLabelText('수요축 값')).toHaveTextContent('블랙')
  })

  it('폼 검증 실패는 서버로 보내지 않고 그 자리에서 알린다', async () => {
    render(<PurchasePanel variantId={11} />)
    await userEvent.type(screen.getByLabelText('구매일'), '2026-07-01')
    await userEvent.click(screen.getByRole('button', { name: '기록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('실지불가')
    expect(api.recordPurchase).not.toHaveBeenCalled()
  })

  it('서버 실패는 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'recordPurchase').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))
    render(<PurchasePanel variantId={11} />)
    await fill()
    await userEvent.click(screen.getByRole('button', { name: '기록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('구매 기록 실패 (HTTP_500)')
  })

  it('기존 구매의 관찰 문맥을 한 줄로 보여준다', async () => {
    vi.spyOn(api, 'listPurchases').mockResolvedValue([observing])
    render(<PurchasePanel variantId={11} />)

    expect(await screen.findByLabelText('관찰 문맥 7')).toHaveTextContent(
      '활성 딜 없음 · 관찰 8일차 · 더 싼 기회 0건',
    )
    expect(screen.getByLabelText('구매가')).toHaveTextContent('899,000원')
  })

  it('CLOSED 구매는 관찰 문맥 대신 발급된 성적표를 그린다 (PUR-04)', async () => {
    vi.spyOn(api, 'listPurchases').mockResolvedValue([closed])
    render(<PurchasePanel variantId={11} />)

    expect(await screen.findByLabelText('성적표 9')).toHaveTextContent(
      '3건 중 2건이 내 구매가보다 쌌습니다 · 기준가보다 79,000원 비쌈 · 기간 내 최저 840,000원',
    )
    // 관찰 문맥은 그리지 않는다 — 관찰은 끝났고 성적표가 그 요약이다.
    expect(screen.queryByLabelText('관찰 문맥 9')).toBeNull()
    expect(screen.getByText('성적표 발급')).toBeInTheDocument() // 상태 칩
  })

  it('variant가 바뀌면 그 variant의 기록을 다시 부른다', async () => {
    const { rerender } = render(<PurchasePanel variantId={11} />)
    await waitFor(() => expect(api.listPurchases).toHaveBeenCalledWith(11))

    rerender(<PurchasePanel variantId={12} />)
    await waitFor(() => expect(api.listPurchases).toHaveBeenCalledWith(12))
  })
})

describe('PurchasePanel — 날짜는 KST로 그린다 (OPS-03)', () => {
  it('UTC 저녁에 기록된 구매는 KST 날짜(다음 날)로 보인다', async () => {
    vi.spyOn(api, 'listPurchases').mockResolvedValue([
      { ...observing, purchasedAt: '2026-07-01T20:00:00Z' }, // KST 2026-07-02 05:00
    ])
    render(<PurchasePanel variantId={11} />)

    const item = await screen.findByRole('listitem')
    expect(item).toHaveTextContent('2026-07-02')
    expect(item).not.toHaveTextContent('2026-07-01')
  })
})
