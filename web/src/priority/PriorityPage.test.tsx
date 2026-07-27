import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { PrioritizedProduct } from '../api/types'
import { PriorityPage } from './PriorityPage'

const waiting = (overrides: Partial<PrioritizedProduct> = {}): PrioritizedProduct => ({
  productId: 1,
  name: '아이폰 17',
  priorityRank: null,
  waiting: true,
  manuallyCompleted: false,
  ...overrides,
})

describe('PriorityPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('목록을 순서 그대로 그린다 — 정렬은 core가 이미 했다', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockResolvedValue([
      waiting({ productId: 1, name: '아이폰 17', priorityRank: 1 }),
      waiting({ productId: 2, name: '갤럭시 S26', priorityRank: null }),
    ])

    render(<PriorityPage />)

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('아이폰 17')
    expect(items[1]).toHaveTextContent('갤럭시 S26')
  })

  it('대기 아닌 제품은 "구매됨/완료" 배지를 단다', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockResolvedValue([waiting({ waiting: false })])

    render(<PriorityPage />)

    expect(await screen.findByText('구매됨/완료')).toBeInTheDocument()
  })

  it('순번을 저장하면 목록을 다시 불러온다', async () => {
    const list = vi.spyOn(api, 'listPrioritizedProducts')
    list
      .mockResolvedValueOnce([waiting({ priorityRank: null })])
      .mockResolvedValueOnce([waiting({ priorityRank: 2 })])
    const setPriority = vi.spyOn(api, 'setPriority').mockResolvedValue()

    render(<PriorityPage />)
    await screen.findByText('순번 미지정')

    await userEvent.type(screen.getByLabelText('순번 입력'), '2')
    await userEvent.click(screen.getByRole('button', { name: '순번 저장' }))

    expect(setPriority).toHaveBeenCalledWith(1, { rank: 2 })
    expect(await screen.findByText('2순위')).toBeInTheDocument()
  })

  it('입력을 비우고 저장하면 순번을 해제한다(null)', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockResolvedValue([waiting({ priorityRank: 1 })])
    const setPriority = vi.spyOn(api, 'setPriority').mockResolvedValue()

    render(<PriorityPage />)
    const input = await screen.findByLabelText('순번 입력')
    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: '순번 저장' }))

    expect(setPriority).toHaveBeenCalledWith(1, { rank: null })
  })

  it('중복 순번은 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockResolvedValue([waiting({ priorityRank: null })])
    vi.spyOn(api, 'setPriority').mockRejectedValue(new ApiFailure(409, 'PRI_DUPLICATE_RANK'))

    render(<PriorityPage />)
    await userEvent.type(await screen.findByLabelText('순번 입력'), '1')
    await userEvent.click(screen.getByRole('button', { name: '순번 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('PRI_DUPLICATE_RANK')
  })

  it('수동 완료로 표시하면 목록을 다시 불러온다', async () => {
    const list = vi.spyOn(api, 'listPrioritizedProducts')
    list
      .mockResolvedValueOnce([waiting({ manuallyCompleted: false })])
      .mockResolvedValueOnce([waiting({ manuallyCompleted: true, waiting: false })])
    const setManuallyCompleted = vi.spyOn(api, 'setManuallyCompleted').mockResolvedValue()

    render(<PriorityPage />)
    await userEvent.click(await screen.findByRole('button', { name: '수동 완료로 표시' }))

    expect(setManuallyCompleted).toHaveBeenCalledWith(1, { manuallyCompleted: true })
    expect(await screen.findByRole('button', { name: '완료 해제' })).toBeInTheDocument()
  })

  it('목록이 비면 그 사실을 말한다', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockResolvedValue([])

    render(<PriorityPage />)

    expect(await screen.findByText(/등록된 제품이 없습니다/)).toBeInTheDocument()
  })

  it('불러오지 못하면 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listPrioritizedProducts').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))

    render(<PriorityPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP_500')
  })
})
