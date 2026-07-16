import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { ReviewQueueItem } from '../api/types'
import { ReviewQueuePage } from './ReviewQueuePage'

/** 이상치는 딜(dealEventId)이 있어 승격·기각 둘 다 된다. 조건 태그는 "왜 싸 보이나"를 말한다. */
const outlier: ReviewQueueItem = {
  id: 9,
  type: 'OUTLIER_LOWER',
  occurrences: 1,
  firstSeenAt: '2026-07-10T00:00:00Z',
  lastSeenAt: '2026-07-10T00:00:00Z',
  sourceUrl: 'https://ppomppu/9',
  subject: '아이폰 17 — 256GB',
  candidateProducts: [],
  conditions: ['카할'],
  payload: { dealEventId: 42, priceFirst: 100_000 },
}

const unclassified: ReviewQueueItem = {
  id: 3,
  type: 'UNCLASSIFIED',
  occurrences: 1,
  firstSeenAt: '2026-07-10T00:00:00Z',
  lastSeenAt: '2026-07-10T00:00:00Z',
  sourceUrl: 'https://ppomppu/1',
  subject: null,
  candidateProducts: ['아이폰 17'],
  conditions: [],
  payload: { title: '아이폰17 특가', productCandidates: [7] },
}

describe('ReviewQueuePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('큐에 쌓인 것을 원문 링크와 함께 보여준다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([unclassified])

    render(<ReviewQueuePage />)

    expect(await screen.findByText(/어느 제품인지 확정하지 못했습니다/)).toBeInTheDocument()
    expect(screen.getByText(/아이폰17 특가/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '원문' })).toHaveAttribute('href', 'https://ppomppu/1')
  })

  /** 죽은 링크를 그리지 않는다. 원문을 잇지 못했다는 사실 자체가 정보다. */
  it('원문을 잇지 못한 항목은 링크 대신 그 사실을 쓴다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([{ ...unclassified, sourceUrl: null }])

    render(<ReviewQueuePage />)
    await screen.findByText(/어느 제품인지 확정하지 못했습니다/)

    expect(screen.queryByRole('link', { name: '원문' })).toBeNull()
    expect(screen.getByText(/원문 링크 없음/)).toBeInTheDocument()
  })

  /**
   * Q-15 승격·기각. 미상 항목은 딜이 없어 **승격할 대상 자체가 없다**(core도 400으로 막는다) —
   * 못 하는 일은 버튼으로 그리지 않는다(과대약속 금지). 이상치는 딜이 있어 둘 다 된다.
   */
  it('이상치는 승격·기각 둘 다, 미상은 기각만 그린다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([outlier, unclassified])

    render(<ReviewQueuePage />)
    await screen.findByRole('button', { name: '승격' })

    expect(screen.getAllByRole('button', { name: '승격' })).toHaveLength(1) // 이상치에만
    expect(screen.getAllByRole('button', { name: '기각' })).toHaveLength(2) // 둘 다 기각은 된다
  })

  it('승격하면 그 항목이 큐에서 내려간다 — 처리됐다는 걸 눈으로 확인시킨다', async () => {
    const list = vi.spyOn(api, 'listReviewQueue')
    list.mockResolvedValueOnce([outlier]).mockResolvedValueOnce([])
    const promote = vi.spyOn(api, 'promoteReviewItem').mockResolvedValue()

    render(<ReviewQueuePage />)
    await userEvent.click(await screen.findByRole('button', { name: '승격' }))

    expect(promote).toHaveBeenCalledWith(9)
    expect(await screen.findByText(/대기 중인 항목이 없습니다/)).toBeInTheDocument()
  })

  it('기각도 같은 길로 간다', async () => {
    const list = vi.spyOn(api, 'listReviewQueue')
    list.mockResolvedValueOnce([unclassified]).mockResolvedValueOnce([])
    const reject = vi.spyOn(api, 'rejectReviewItem').mockResolvedValue()

    render(<ReviewQueuePage />)
    await userEvent.click(await screen.findByRole('button', { name: '기각' }))

    expect(reject).toHaveBeenCalledWith(3)
    expect(await screen.findByText(/대기 중인 항목이 없습니다/)).toBeInTheDocument()
  })

  /** 이미 처리된 항목을 또 누르면 core가 404를 준다 — 삼키지 않고 code를 그대로 보여준다. */
  it('처리 실패는 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([unclassified])
    vi.spyOn(api, 'rejectReviewItem').mockRejectedValue(new ApiFailure(404, 'REVIEW_ITEM_NOT_FOUND'))

    render(<ReviewQueuePage />)
    await userEvent.click(await screen.findByRole('button', { name: '기각' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('REVIEW_ITEM_NOT_FOUND')
  })

  /**
   * 접힌 중복을 숨기지 않는다. `occurrences > 1`은 재처리 멱등이 없다는 증거다(Q-27 ④) —
   * 조용히 하나로 보여주면 결함이 사라진 것처럼 보인다.
   */
  it('같은 항목이 여러 번 쌓였으면 그 횟수를 드러낸다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([{ ...unclassified, occurrences: 47 }])

    render(<ReviewQueuePage />)

    expect(await screen.findByText(/47번 다시 쌓였습니다/)).toBeInTheDocument()
  })

  it('한 번만 쌓인 항목엔 횟수를 붙이지 않는다 — 군더더기', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([unclassified])

    render(<ReviewQueuePage />)
    await screen.findByText(/어느 제품인지 확정하지 못했습니다/)

    expect(screen.queryByText(/다시 쌓였습니다/)).toBeNull()
  })

  it('큐가 비면 비었다고 말한다 — 빈 화면이 아니라', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([])

    render(<ReviewQueuePage />)

    expect(await screen.findByText(/대기 중인 항목이 없습니다/)).toBeInTheDocument()
  })

  it('불러오지 못하면 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))

    render(<ReviewQueuePage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP_500')
  })
})

describe('ReviewQueuePage — 이상치는 왜 싸 보이는지 화면에 나온다', () => {
  it('조건 태그를 그리고, 배송비 미상이면 하한임을 말한다', async () => {
    const outlier: ReviewQueueItem = {
      id: 9,
      type: 'OUTLIER_LOWER',
      occurrences: 1,
      firstSeenAt: '2026-07-10T00:00:00Z',
      lastSeenAt: '2026-07-10T00:00:00Z',
      sourceUrl: 'https://ppomppu/9',
      subject: '아이폰 17 — 256GB',
      candidateProducts: [],
      conditions: ['배송비미상', '카할'],
      payload: { priceFirst: 700000 },
    }
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([outlier])

    render(<ReviewQueuePage />)

    expect(await screen.findByText(/조건부: 배송비미상 · 카할/)).toBeInTheDocument()
    expect(screen.getByText(/실제 결제가는 더 높습니다/)).toBeInTheDocument()
  })
})
