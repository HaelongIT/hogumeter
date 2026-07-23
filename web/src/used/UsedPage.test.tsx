import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { UsedPage } from './UsedPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: '스마트폰',
  demandAxisMode: 'GROUPED' as const,
  axes: [],
  variants: [],
}

/**
 * 실제로 겪은 함정: 평가기의 "중고 검색" 목록은 마운트 시 한 번만 불러온다. 등록 직후엔 그 목록에
 * 방금 만든 검색이 없어 페이지를 새로고침해야 보였다 — 브라우저로 직접 확인하다 발견했다.
 */
describe('UsedPage — 등록한 검색이 평가기 목록에 곧바로 보인다', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'getComparison').mockResolvedValue({ axes: [], rows: [] })
  })

  it('등록 성공 후 평가기가 새 검색 목록을 다시 불러온다', async () => {
    const listUsedSearches = vi.spyOn(api, 'listUsedSearches').mockResolvedValue([])
    vi.spyOn(api, 'registerUsedSearch').mockResolvedValue({ usedSearchId: 5 })
    const user = userEvent.setup()
    render(<UsedPage />)

    await user.selectOptions((await screen.findAllByRole('combobox', { name: '제품' }))[0]!, '1')
    await waitFor(() => expect(listUsedSearches).toHaveBeenCalledTimes(2)) // 등록 폼 + 평가기, 초기 로드

    listUsedSearches.mockResolvedValue([
      {
        usedSearchId: 5,
        platform: 'BUNJANG',
        required: ['아이폰17'],
        exclude: [],
        targetPrice: null,
        pollIntervalMin: 10,
        bonusGroups: [],
      },
    ])
    await user.type(screen.getByLabelText(/필수 키워드/), '아이폰17')
    await user.click(screen.getByRole('button', { name: '등록' }))

    // 평가기가 다시 불러 새 검색을 셀렉트에 반영한다 — 새로고침 없이.
    await waitFor(() => {
      const evaluateSelect = screen.getByRole('combobox', { name: '중고 검색' })
      expect(evaluateSelect).toHaveTextContent('#5')
    })
  })
})
