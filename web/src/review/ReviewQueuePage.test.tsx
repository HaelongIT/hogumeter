import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { ReviewQueueItem } from '../api/types'
import { ReviewQueuePage } from './ReviewQueuePage'

const unclassified: ReviewQueueItem = {
  id: 3,
  type: 'UNCLASSIFIED',
  occurrences: 1,
  firstSeenAt: '2026-07-10T00:00:00Z',
  lastSeenAt: '2026-07-10T00:00:00Z',
  sourceUrl: 'https://ppomppu/1',
  candidateProducts: ['아이폰 17'],
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

  /** 과대약속 금지 — 아직 못 하는 일을 버튼으로 그리지 않는다(승격·기각 REST 부재, Q-15). */
  it('승격·기각 버튼을 그리지 않고, 못 한다는 사실을 말한다', async () => {
    vi.spyOn(api, 'listReviewQueue').mockResolvedValue([unclassified])

    render(<ReviewQueuePage />)
    await screen.findByText(/어느 제품인지 확정하지 못했습니다/)

    expect(screen.queryByRole('button', { name: /승격|기각/ })).toBeNull()
    expect(screen.getByRole('note')).toHaveTextContent(/승격.*기각.*아직/)
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
