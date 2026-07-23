import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { UsedComparisonPage } from './UsedComparisonPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: '스마트폰',
  demandAxisMode: 'GROUPED' as const,
  axes: [],
  variants: [],
}

describe('UsedComparisonPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'defineComparisonAxes').mockResolvedValue([{ id: 1, name: '배터리%' }])
    vi.spyOn(api, 'addListingNote').mockResolvedValue({ noteId: 1 })
    vi.spyOn(api, 'promoteAxisValue').mockResolvedValue(undefined)
  })

  it('매물이 없으면 실 폴링이 필요하다고 정직하게 말한다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({ axes: [], rows: [] })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')

    expect(await screen.findByText(/실제로 폴링돼야/)).toBeInTheDocument()
  })

  it('축은 추가 전용이라는 사실을 밝힌다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({ axes: [], rows: [] })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')

    expect(await screen.findByRole('note')).toHaveTextContent('추가만')
  })

  it('승격 안 된 축은 빈칸(체크리스트)로, 승격된 축은 값으로 보여준다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({
      axes: [
        { id: 1, name: '배터리%' },
        { id: 2, name: '구성' },
      ],
      rows: [
        {
          listingId: 10,
          title: '아이폰 17 256',
          price: 800000,
          url: 'https://m.bunjang.co.kr/1',
          axisValues: { '1': '92%' },
          notes: ['잔기스 있음'],
        },
      ],
    })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')

    const table = await screen.findByRole('table', { name: '병렬 비교표' })
    expect(table).toHaveTextContent('92%')
    expect(screen.getByLabelText('구성 미확인')).toBeInTheDocument() // 빈칸이 값이 아니라 미확인 표식
    expect(table).toHaveTextContent('잔기스 있음')
  })

  it('축 이름을 추가하면 core를 부르고 다시 불러온다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({ axes: [], rows: [] })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await user.type(screen.getByLabelText('비교축 이름'), '배터리%')
    await user.click(screen.getByRole('button', { name: '축 추가' }))

    await waitFor(() => expect(api.defineComparisonAxes).toHaveBeenCalledWith(1, ['배터리%']))
  })

  it('메모를 추가하면 listingId로 core를 부른다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({
      axes: [],
      rows: [{ listingId: 10, title: '아이폰 17', price: 800000, url: null, axisValues: {}, notes: [] }],
    })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await user.type(await screen.findByLabelText('아이폰 17 메모 입력'), '잔기스 있음')
    await user.click(screen.getByRole('button', { name: '메모 추가' }))

    await waitFor(() => expect(api.addListingNote).toHaveBeenCalledWith(10, '잔기스 있음'))
  })

  it('축 값을 입력하고 승격을 누르면 axisId·value로 core를 부른다', async () => {
    vi.spyOn(api, 'getComparison').mockResolvedValue({
      axes: [{ id: 7, name: '배터리%' }],
      rows: [{ listingId: 10, title: '아이폰 17', price: 800000, url: null, axisValues: {}, notes: [] }],
    })
    const user = userEvent.setup()
    render(<UsedComparisonPage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '제품' }), '1')
    await user.type(await screen.findByLabelText('아이폰 17 배터리% 값 입력'), '92%')
    await user.click(screen.getByRole('button', { name: '승격' }))

    await waitFor(() => expect(api.promoteAxisValue).toHaveBeenCalledWith(10, { axisId: 7, value: '92%' }))
  })
})
