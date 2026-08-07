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

  /**
   * FE-02(코드리뷰 20260806) — cleanup 가드가 없으면, 먼저 보낸 요청(옛 variant)의 응답이
   * 나중에 도착해 최종 화면을 덮어쓴다. "이미 샀는가"를 엉뚱한 variant 기준으로 보여주게 된다.
   */
  it('variant를 빠르게 바꾸면 늦게 도착한 이전 variant의 응답이 최신 화면을 덮어쓰지 않는다', async () => {
    let resolveFirst: (value: PurchaseObservation[]) => void = () => {}
    const first = new Promise<PurchaseObservation[]>((resolve) => {
      resolveFirst = resolve
    })
    const spy = vi.spyOn(api, 'listPurchases')
    spy.mockImplementationOnce(() => first) // variantId=11 — 응답이 늦게 도착
    spy.mockResolvedValueOnce([observing]) // variantId=12 — 먼저 도착

    const { rerender } = render(<PurchasePanel variantId={11} />)
    await waitFor(() => expect(api.listPurchases).toHaveBeenCalledWith(11))

    rerender(<PurchasePanel variantId={12} />)
    await screen.findByLabelText('관찰 문맥 7') // variantId=12 응답 반영됨

    resolveFirst([]) // 이제야 variantId=11의 옛(빈) 응답이 도착
    await new Promise((r) => setTimeout(r, 0))

    // 옛 응답이 화면을 "기록 없음"으로 덮어쓰면 안 된다 — 여전히 12의 데이터를 보여줘야 한다.
    expect(screen.getByLabelText('관찰 문맥 7')).toBeInTheDocument()
    expect(screen.queryByText('이 variant의 구매 기록이 없습니다.')).not.toBeInTheDocument()
  })
})

describe('PurchasePanel — WATCH [샀어요] 프리필(Q-83 ②)', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listPurchases').mockResolvedValue([])
    vi.spyOn(api, 'recordPurchase').mockResolvedValue({ purchaseId: 7 })
  })

  it('딜 가격·오늘 날짜를 미리 채우고, 딜의 조건 태그로 안내를 보여준다', async () => {
    render(
      <PurchasePanel
        variantId={11}
        prefill={{ dealEventId: 42, dealPrice: 850_000, appliedConditions: ['배송비미상'] }}
      />,
    )

    expect(await screen.findByLabelText(/실지불가/)).toHaveValue('850000')
    expect(screen.getByRole('note')).toHaveTextContent('배송비를 못 읽어 하한입니다')
  })

  it('제출하면 연결 딜을 함께 보낸다', async () => {
    render(
      <PurchasePanel
        variantId={11}
        prefill={{ dealEventId: 42, dealPrice: 850_000, appliedConditions: null }}
      />,
    )
    // 실지불가·구매일 모두 이미 프리필돼 있다 — 그 값 그대로 제출.
    await screen.findByDisplayValue('850000')
    await userEvent.click(screen.getByRole('button', { name: '기록' }))

    await waitFor(() =>
      expect(api.recordPurchase).toHaveBeenCalledWith(expect.objectContaining({ linkedDealEventId: 42 })),
    )
  })

  it('조건 태그가 없으면 관측가 안내만 — 늘 뜨는 배송비 경고를 달지 않는다', async () => {
    render(
      <PurchasePanel variantId={11} prefill={{ dealEventId: 42, dealPrice: 850_000, appliedConditions: [] }} />,
    )

    expect(await screen.findByRole('note')).toHaveTextContent('이 딜의 관측가(배송비 포함)입니다')
  })

  it('프리필 없이 쓰면 안내가 안 뜨고 폼은 비어 있다 — 기존 흐름 그대로', async () => {
    render(<PurchasePanel variantId={11} />)

    expect(await screen.findByLabelText(/실지불가/)).toHaveValue('')
    expect(screen.queryByRole('note')).toBeNull()
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
