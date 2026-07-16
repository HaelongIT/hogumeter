import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import { RegistrationPage } from './RegistrationPage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: 'phone',
  demandAxisMode: 'GROUPED' as const,
  axes: [{ axisType: 'PRICE' as const, name: '용량', allowedValues: ['256GB', '512GB'] }],
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

  /**
   * Q-66 ②: `product_axis`는 등록이 쓰기만 하고 **아무도 읽지 않는 테이블**이었다. 수요축은 variant를
   * 나누지 않으므로 variant 목록에 흔적이 없다 — 여기서 못 보면 자기가 무엇을 수요축으로 등록했는지
   * 확인할 길이 자체가 없다.
   */
  it('등록한 축을 유형과 함께 보여준다 — 수요축은 variant 목록에 흔적이 없다', async () => {
    vi.mocked(api.listProducts).mockResolvedValue([
      {
        ...iphone,
        axes: [
          { axisType: 'PRICE' as const, name: '용량', allowedValues: ['256GB', '512GB'] },
          { axisType: 'DEMAND' as const, name: '색상', allowedValues: ['블랙', '화이트'] },
        ],
      },
    ])
    render(<RegistrationPage />)

    const list = await screen.findByRole('list', { name: '등록된 제품' })
    expect(within(list).getByText(/용량\(가격축\)/)).toBeInTheDocument()
    expect(within(list).getByText(/색상\(수요축\)/)).toBeInTheDocument()
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

describe('RegistrationPage — 등록 다음에 무엇을 할지 알려준다', () => {
  const registered = {
    productId: 9,
    name: '아이폰 17',
    category: 'phone',
    demandAxisMode: 'GROUPED' as const,
    axes: [{ axisType: 'PRICE' as const, name: '용량', allowedValues: ['256GB', '512GB'] }],
    variants: [
      { variantId: 91, label: '256GB', priceAxisValues: { 용량: '256GB' } },
      { variantId: 92, label: '512GB', priceAxisValues: { 용량: '512GB' } },
    ],
  }

  const fillAndSubmit = async () => {
    await userEvent.type(screen.getByLabelText('제품명'), '아이폰 17')
    await userEvent.type(screen.getByLabelText(/축 1 값/), '256GB, 512GB')
    await userEvent.click(screen.getByRole('button', { name: '등록' }))
  }

  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
    vi.spyOn(api, 'registerProduct').mockResolvedValue({ productId: 9 })
    vi.spyOn(api, 'listVariants').mockResolvedValue(registered.variants)
  })

  it('등록에 성공하면 variant를 나열하고, 어느 것을 볼지는 사람이 고른다', async () => {
    render(<RegistrationPage />)
    await fillAndSubmit()

    // variant가 둘인데 하나를 골라주지 않는다 — 지어내지 않는다(절대 원칙 2).
    expect(await screen.findByRole('button', { name: '256GB 판단 보기' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '512GB 판단 보기' })).toBeInTheDocument()
    expect(api.listVariants).toHaveBeenCalledWith(9)
  })

  it('variant를 고르면 그 variantId로 판단 화면을 연다', async () => {
    const onOpenDecision = vi.fn()
    render(<RegistrationPage onOpenDecision={onOpenDecision} />)
    await fillAndSubmit()
    await userEvent.click(await screen.findByRole('button', { name: '512GB 판단 보기' }))

    expect(onOpenDecision).toHaveBeenCalledWith(92)
  })

  it('variant 조회가 실패해도 등록 성공을 부정하지 않는다', async () => {
    vi.spyOn(api, 'listVariants').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))
    render(<RegistrationPage />)
    await fillAndSubmit()

    expect(await screen.findByText(/등록했습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /판단 보기/ })).not.toBeInTheDocument()
  })
})

describe('RegistrationPage — 없는 손잡이를 그리지 않는다', () => {
  /**
   * Q-66 ① 해소: 분리가 **실제로 표본을 나눈다.** 예전엔 SPLIT이 저장만 되고 아무 코드도 그 값을 보지
   * 않아 "아직 안 나눕니다"라고 밝혀야 했다 — 이제 그 문장은 거짓이라 지웠다. 대신 고르면 무슨 일이
   * 생기는지 말한다(값별 기준가 + 판별 못 한 딜은 빠짐).
   */
  it('분리를 고르면 무슨 일이 생기는지 말한다 — 값별 기준가와 미상 딜 제외', () => {
    render(<RegistrationPage />)

    expect(screen.getByText(/수요축 값별로 기준가를 따로/)).toBeInTheDocument()
    expect(screen.getByText(/판별하지 못한 딜은 기준가에서 빠집니다/)).toBeInTheDocument()
    expect(screen.queryByText(/아직 표본을 나누지 않습니다/)).toBeNull()
  })

  /**
   * Q-66 ②: 축 유형은 이제 **동작한다.** 예전엔 모든 축이 가격축이라 색상을 넣으면 variant가 곱해져
   * 표본이 쪼개졌고, 화면은 그 사실을 경고만 했다. 이제 사람이 수요축으로 고르면 실제로 안 나뉜다.
   */
  it('색상을 수요축으로 고르면 variant가 곱해지지 않는다 — 표본이 쪼개지지 않는다', async () => {
    const user = userEvent.setup()
    render(<RegistrationPage />)

    await user.type(screen.getByLabelText(/제품명/), '아이폰 17')
    await user.type(screen.getByLabelText(/축 1 값/), '256GB, 512GB')
    await user.click(screen.getByRole('button', { name: '축 추가' }))
    await user.type(screen.getByLabelText(/축 2 이름/), '색상')
    await user.selectOptions(screen.getByLabelText(/축 2 유형/), 'DEMAND')
    await user.type(screen.getByLabelText(/축 2 값/), '블랙, 화이트')

    // 가격축(2) × 수요축(2) = 4가 아니라 2다.
    const preview = await screen.findByRole('region', { name: '생성될 variant' })
    expect(within(preview).getByText('생성될 variant 2개')).toBeInTheDocument()
    expect(within(preview).queryByText('256GB / 블랙')).toBeNull()
  })

  it('축 유형의 기본은 가격축이다 — 대부분의 축이 가격을 가른다', () => {
    render(<RegistrationPage />)

    expect(screen.getByLabelText(/축 1 유형/)).toHaveValue('PRICE')
  })
})
