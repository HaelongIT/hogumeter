import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import { UsedEvaluatePage } from './UsedEvaluatePage'

const iphone = {
  productId: 1,
  name: '아이폰 17',
  category: '스마트폰',
  demandAxisMode: 'GROUPED' as const,
  axes: [],
  variants: [],
}

const search = {
  usedSearchId: 5,
  platform: 'BUNJANG',
  required: ['아이폰17', '256'],
  exclude: [],
  targetPrice: 900000,
  pollIntervalMin: 10,
  bonusGroups: [],
}

describe('UsedEvaluatePage', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([iphone])
    vi.spyOn(api, 'listUsedSearches').mockResolvedValue([search])
  })

  it('URL은 실제로 접속하지 않는다는 사실을 밝힌다', async () => {
    render(<UsedEvaluatePage />)

    expect(await screen.findByRole('note')).toHaveTextContent('실제로 접속하지 않습니다')
  })

  it('MANUAL 제출 → 결과에 가격 맥락·위험 신호를 보여준다', async () => {
    vi.spyOn(api, 'evaluateListing').mockResolvedValue({
      needsInput: null,
      listing: { title: '아이폰 17 256 S급', price: 850000, url: 'https://m.bunjang.co.kr/1' },
      priceContext: { benchmarkComparisonPercent: 15, activeSnapshotPrices: [800000, 900000], source: '번개장터 활성 매물' },
      riskSignals: [{ category: '업자 레퍼토리 키워드', detail: '이민 급처' }],
    })
    const user = userEvent.setup()
    render(<UsedEvaluatePage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '중고 검색' }), '5')
    await user.type(screen.getByLabelText('제목'), '아이폰 17 256 S급')
    await user.type(screen.getByLabelText(/가격/), '850000')
    await user.click(screen.getByRole('button', { name: '평가' }))

    expect(await screen.findByText('아이폰 17 256 S급')).toBeInTheDocument()
    expect(screen.getByText(/850,000원/)).toBeInTheDocument()
    expect(screen.getByLabelText('가격 맥락')).toHaveTextContent('15%')
    expect(screen.getByLabelText('위험 신호')).toHaveTextContent('이민 급처')
    // 판정 문구를 쓰지 않는다(AC-14) — 나열만
    expect(screen.getByLabelText('위험 신호').textContent).not.toMatch(/사기|위험합니다/)
  })

  it('TEXT 실패 → MANUAL 요청 안내로 다음 단계를 자동 전환한다', async () => {
    vi.spyOn(api, 'evaluateListing').mockResolvedValue({
      needsInput: 'MANUAL',
      listing: null,
      priceContext: null,
      riskSignals: null,
    })
    const user = userEvent.setup()
    render(<UsedEvaluatePage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '중고 검색' }), '5')
    await user.selectOptions(screen.getByRole('combobox', { name: '입력 방식' }), 'TEXT')
    await user.type(screen.getByLabelText(/붙여넣은 본문/), '가격은 쪽지로')
    await user.click(screen.getByRole('button', { name: '평가' }))

    expect(await screen.findByRole('status', { name: '입력 요청' })).toHaveTextContent('직접 입력')
    await waitFor(() => expect(screen.getByRole('combobox', { name: '입력 방식' })).toHaveValue('MANUAL'))
  })

  it('가격 맥락에서 variant 미지정을 정직하게 밝힌다 — 0%로 지어내지 않는다', async () => {
    vi.spyOn(api, 'evaluateListing').mockResolvedValue({
      needsInput: null,
      listing: { title: 'x', price: 100000, url: null },
      priceContext: { benchmarkComparisonPercent: null, activeSnapshotPrices: [], source: '번개장터 활성 매물' },
      riskSignals: [],
    })
    const user = userEvent.setup()
    render(<UsedEvaluatePage />)

    await user.selectOptions(await screen.findByRole('combobox', { name: '중고 검색' }), '5')
    await user.type(screen.getByLabelText('제목'), 'x')
    await user.type(screen.getByLabelText(/가격/), '100000')
    await user.click(screen.getByRole('button', { name: '평가' }))

    expect(await screen.findByLabelText('가격 맥락')).toHaveTextContent('비교 안 함')
  })
})
