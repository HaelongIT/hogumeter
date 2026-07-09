import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { RegistrationPage } from './RegistrationPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: 'phone',
  demandAxisMode: 'GROUPED' as const,
  variants: [
    { variantId: 11, label: '256GB', priceAxisValues: { 용량: '256GB' } },
    { variantId: 12, label: '512GB', priceAxisValues: { 용량: '512GB' } },
  ],
}

describe('RegistrationPage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
    vi.spyOn(api, 'registerProduct').mockResolvedValue({ productId: 1 })
  })

  it('네이버 후보 검색이 없는 이유를 숨기지 않는다 (Q-3)', async () => {
    render(<RegistrationPage />)

    expect(await screen.findByRole('note')).toHaveTextContent('네이버 후보 검색은 API 키가 없어')
  })

  it('폼을 채워 등록하면 축 값 조합대로 variant를 만들어 보낸다', async () => {
    const user = userEvent.setup()
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.type(screen.getByLabelText(/축 1 값/), '256GB, 512GB')
    await user.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(api.registerProduct).toHaveBeenCalledOnce())
    expect(vi.mocked(api.registerProduct).mock.calls[0]![0]).toMatchObject({
      name: '아이폰 17',
      demandAxisMode: 'GROUPED',
      variants: [
        { label: '256GB', priceAxisValues: { 용량: '256GB' } },
        { label: '512GB', priceAxisValues: { 용량: '512GB' } },
      ],
    })
  })

  it('축을 추가하면 조합대로 variant가 늘어난다 (REG-02 수용 기준)', async () => {
    const user = userEvent.setup()
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.type(screen.getByLabelText(/축 1 값/), '256GB, 512GB')
    await user.click(screen.getByRole('button', { name: '축 추가' }))
    await user.type(screen.getByLabelText(/축 2 이름/), '색상')
    await user.type(screen.getByLabelText(/축 2 값/), '블랙, 화이트')

    // 조합은 눈으로 확인해야 한다 — 곱셈으로 늘어난다
    const preview = await screen.findByRole('region', { name: '생성될 variant' })
    expect(within(preview).getByText('생성될 variant 4개')).toBeInTheDocument()
    expect(within(preview).getByText('256GB / 블랙')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '등록' }))

    await waitFor(() => expect(api.registerProduct).toHaveBeenCalledOnce())
    expect(vi.mocked(api.registerProduct).mock.calls[0]![0].variants).toHaveLength(4)
  })

  it('등록 성공 후 목록을 다시 불러온다 — 사용자가 결과를 눈으로 확인해야 한다', async () => {
    const user = userEvent.setup()
    vi.mocked(api.listProducts).mockResolvedValueOnce([]).mockResolvedValueOnce([iphone])
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.type(screen.getByLabelText(/축 1 값/), '256GB, 512GB')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('아이폰 17')).toBeInTheDocument()
  })

  it('variantId를 노출한다 — 기준가·신호·주기 조회가 전부 이걸 요구한다', async () => {
    vi.mocked(api.listProducts).mockResolvedValue([iphone])
    render(<RegistrationPage />)

    const list = await screen.findByRole('list', { name: '등록된 제품' })
    expect(within(list).getByText('#11')).toBeInTheDocument()
    expect(within(list).getByText('#12')).toBeInTheDocument()
  })

  it('폼 검증 실패는 서버로 보내지 않고 그 자리에서 알린다', async () => {
    const user = userEvent.setup()
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.click(screen.getByRole('button', { name: '등록' })) // 축 값 없음

    expect(await screen.findByRole('alert')).toHaveTextContent('값을 하나 이상')
    expect(api.registerProduct).not.toHaveBeenCalled()
  })

  it('서버 실패는 code를 그대로 보여준다 — 삼키지 않는다', async () => {
    const user = userEvent.setup()
    const { ApiFailure } = await import('../api/client')
    vi.mocked(api.registerProduct).mockRejectedValue(new ApiFailure(400, 'BM_INVALID_PERIOD'))
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.type(screen.getByLabelText(/축 1 값/), '256GB')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('BM_INVALID_PERIOD')
  })

  it('core가 죽어 있으면 목록 실패를 알린다', async () => {
    vi.mocked(api.listProducts).mockRejectedValue(new Error('network'))
    render(<RegistrationPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('core가 떠 있는지')
  })
})
