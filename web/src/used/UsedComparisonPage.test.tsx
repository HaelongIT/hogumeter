import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  /** 대기 중인 프라미스 체인을 흘려보낸다(마이크로태스크 여러 홉을 안전하게 통과). */
  const flush = async () => {
    for (let i = 0; i < 5; i++) await Promise.resolve()
  }

  /**
   * FE-03(코드리뷰 20260806) — 취소·세대 가드가 없으면, 먼저 보낸 요청(옛 product)의 응답이
   * 나중에 도착해 최종 화면을 덮어쓴다. 가격·컨디션을 나란히 보고 사는 화면이라 오판 위험이 크다.
   */
  it('제품을 빠르게 바꾸면 늦게 도착한 이전 제품의 응답이 최신 화면을 덮어쓰지 않는다', async () => {
    const galaxy = {
      productId: 2,
      name: '갤럭시 25',
      category: '스마트폰',
      demandAxisMode: 'GROUPED' as const,
      axes: [],
      variants: [],
    }
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone, galaxy])

    let resolveFirst: (value: { axes: []; rows: [] }) => void = () => {}
    const first = new Promise<{ axes: []; rows: [] }>((resolve) => {
      resolveFirst = resolve
    })
    const spy = vi.spyOn(api, 'getComparison')
    spy.mockImplementationOnce(() => first) // productId=1 — 응답이 늦게 도착
    spy.mockResolvedValueOnce({
      axes: [],
      rows: [{ listingId: 2, title: '갤럭시 25 매물', price: 700_000, url: null, axisValues: {}, notes: [] }],
    })

    render(<UsedComparisonPage />)

    const select = await screen.findByRole('combobox', { name: '제품' })
    await act(async () => {
      fireEvent.change(select, { target: { value: '1' } })
    })
    expect(api.getComparison).toHaveBeenCalledWith(1)

    await act(async () => {
      fireEvent.change(select, { target: { value: '2' } })
      await flush()
    })

    // productId=2 응답이 반영됨 — 옛(productId=1) 응답은 아직 안 왔다.
    expect(screen.getByText(/갤럭시 25 매물/)).toBeInTheDocument()

    await act(async () => {
      resolveFirst({ axes: [], rows: [] }) // 이제야 productId=1의 옛(빈) 응답이 도착
      await flush()
    })

    // 옛 응답이 화면을 "매물 없음"으로 덮어쓰면 안 된다 — 여전히 갤럭시 데이터를 보여줘야 한다.
    expect(screen.getByText(/갤럭시 25 매물/)).toBeInTheDocument()
    expect(screen.queryByText(/비교할 매물이 없습니다/)).not.toBeInTheDocument()
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
