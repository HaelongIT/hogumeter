import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import { UsedSearchPage } from './UsedSearchPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: '스마트폰',
  demandAxisMode: 'GROUPED' as const,
  axes: [],
  variants: [],
}

describe('UsedSearchPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'listUsedSearches').mockResolvedValue([])
    vi.spyOn(api, 'registerUsedSearch').mockResolvedValue({ usedSearchId: 5 })
  })

  it('제품을 고르기 전엔 등록 폼을 그리지 않는다', async () => {
    render(<UsedSearchPage />)

    await screen.findByRole('combobox', { name: '제품' })
    expect(screen.queryByRole('form', { name: '중고 검색 등록 폼' })).not.toBeInTheDocument()
  })

  it('제품을 고르면 등록된 검색을 불러오고, 폼 제출은 3계층 필터를 명령으로 만든다', async () => {
    const user = userEvent.setup()
    render(<UsedSearchPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await waitFor(() => expect(api.listUsedSearches).toHaveBeenCalledWith(1))

    await user.type(screen.getByLabelText(/필수 키워드/), '아이폰17, 256')
    await user.type(screen.getByLabelText(/제외 키워드/), '파손')
    await user.type(screen.getByLabelText('키워드'), '미개봉, 새제품')
    await user.type(screen.getByLabelText(/목표가/), '800000')
    await user.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(api.registerUsedSearch).toHaveBeenCalledOnce())
    expect(vi.mocked(api.registerUsedSearch).mock.calls[0]![1]).toMatchObject({
      required: ['아이폰17', '256'],
      exclude: ['파손'],
      bonusGroups: [{ keywords: ['미개봉', '새제품'], mode: 'TRIGGER' }],
      targetPrice: 800000,
    })
  })

  it('등록된 검색 목록을 보여준다', async () => {
    vi.spyOn(api, 'listUsedSearches').mockResolvedValue([
      {
        usedSearchId: 5,
        platform: 'BUNJANG',
        required: ['아이폰17', '256'],
        exclude: [],
        targetPrice: 800000,
        pollIntervalMin: 10,
        bonusGroups: [],
      },
    ])
    const user = userEvent.setup()
    render(<UsedSearchPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')

    const item = await screen.findByText(/아이폰17 \+ 256/)
    expect(item).toHaveTextContent('800,000원')
    expect(item).toHaveTextContent('10분 주기')
  })

  it('필수 키워드 없이 제출하면 core를 부르지 않고 에러를 보여준다', async () => {
    const user = userEvent.setup()
    render(<UsedSearchPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('필수 키워드')
    expect(api.registerUsedSearch).not.toHaveBeenCalled()
  })

  it('서버 실패는 code를 보여준다 — 삼키지 않는다', async () => {
    vi.spyOn(api, 'registerUsedSearch').mockRejectedValue(new ApiFailure(400, 'REG_INVALID_PRODUCT'))
    const user = userEvent.setup()
    render(<UsedSearchPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await user.type(screen.getByLabelText(/필수 키워드/), '아이폰17')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('REG_INVALID_PRODUCT')
  })
})
