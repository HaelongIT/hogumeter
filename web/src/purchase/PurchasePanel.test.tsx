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

  it('variant가 바뀌면 그 variant의 기록을 다시 부른다', async () => {
    const { rerender } = render(<PurchasePanel variantId={11} />)
    await waitFor(() => expect(api.listPurchases).toHaveBeenCalledWith(11))

    rerender(<PurchasePanel variantId={12} />)
    await waitFor(() => expect(api.listPurchases).toHaveBeenCalledWith(12))
  })
})
