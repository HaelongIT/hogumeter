import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import type { WatchItemView } from '../api/types'
import { WatchPage } from './WatchPage'

const activeItem = (overrides: Partial<WatchItemView> = {}): WatchItemView => ({
  watchItemId: 1,
  dealEventId: 42,
  note: '아이폰 17 특가',
  state: 'ACTIVE',
  pinnedAt: '2026-07-01T00:00:00Z',
  resolvedAt: null,
  currentPriceLast: 850_000,
  dealStatus: 'ACTIVE',
  reviveUnacknowledged: false,
  ...overrides,
})

describe('WatchPage — 활성 탭', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'listResolvedWatchItems').mockResolvedValue([])
  })

  it('활성 탭이 기본으로 열리고 핀 목록을 보여준다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([activeItem()])

    render(<WatchPage />)

    expect(await screen.findByText('아이폰 17 특가')).toBeInTheDocument()
    expect(screen.getByText('현재가 850,000원')).toBeInTheDocument()
  })

  it('핀이 없으면 그 사실을 말한다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([])

    render(<WatchPage />)

    expect(await screen.findByText(/핀한 딜이 없습니다/)).toBeInTheDocument()
  })

  it('딜 ID로 핀을 걸면 목록을 다시 불러온다', async () => {
    const list = vi.spyOn(api, 'listActiveWatchItems')
    list.mockResolvedValueOnce([]).mockResolvedValueOnce([activeItem()])
    const pin = vi.spyOn(api, 'pinDeal').mockResolvedValue({ watchItemId: 1 })

    render(<WatchPage />)
    await screen.findByText(/핀한 딜이 없습니다/)

    await userEvent.type(screen.getByLabelText('딜 ID'), '42')
    await userEvent.type(screen.getByLabelText('메모'), '아이폰 17 특가')
    await userEvent.click(screen.getByRole('button', { name: '핀하기' }))

    expect(pin).toHaveBeenCalledWith({ dealEventId: 42, note: '아이폰 17 특가' })
    expect(await screen.findByText('아이폰 17 특가')).toBeInTheDocument()
  })

  it('핀할 수 없는 딜(이미 종료)은 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([])
    vi.spyOn(api, 'pinDeal').mockRejectedValue(new ApiFailure(400, 'WATCH_DEAL_NOT_PINNABLE'))

    render(<WatchPage />)
    await userEvent.type(await screen.findByLabelText('딜 ID'), '99')
    await userEvent.click(screen.getByRole('button', { name: '핀하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('WATCH_DEAL_NOT_PINNABLE')
  })

  it('샀어요를 누르면 결말 처리하고 활성 목록에서 사라진다', async () => {
    const list = vi.spyOn(api, 'listActiveWatchItems')
    list.mockResolvedValueOnce([activeItem()]).mockResolvedValueOnce([])
    const markBought = vi
      .spyOn(api, 'markWatchItemBought')
      .mockResolvedValue({ variantId: 5, dealEventId: 42, dealPrice: 850_000, appliedConditions: null })

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '샀어요' }))

    expect(markBought).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/핀한 딜이 없습니다/)).toBeInTheDocument()
  })

  /** Q-83 ②: 미분류 딜이 아니면 프리필 재료로 onBought를 불러 판단 화면 이동을 위임한다. */
  it('샀어요 성공 시 미분류 아닌 딜이면 onBought에 프리필 재료를 실어 부른다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValueOnce([activeItem()]).mockResolvedValueOnce([])
    vi.spyOn(api, 'markWatchItemBought').mockResolvedValue({
      variantId: 5,
      dealEventId: 42,
      dealPrice: 850_000,
      appliedConditions: ['배송비미상'],
    })
    const onBought = vi.fn()

    render(<WatchPage onBought={onBought} />)
    await userEvent.click(await screen.findByRole('button', { name: '샀어요' }))

    await screen.findByText(/핀한 딜이 없습니다/)
    expect(onBought).toHaveBeenCalledWith({
      variantId: 5,
      dealEventId: 42,
      dealPrice: 850_000,
      appliedConditions: ['배송비미상'],
    })
  })

  /** 미분류 딜(variant 없음)은 판단 화면으로 갈 수 없다 — 이동하지 않고 사실을 알린다(지어내지 않는다). */
  it('샀어요 성공 시 미분류 딜이면 onBought를 안 부르고 그 사실을 안내한다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValueOnce([activeItem()]).mockResolvedValueOnce([])
    vi.spyOn(api, 'markWatchItemBought').mockResolvedValue({
      variantId: null,
      dealEventId: 42,
      dealPrice: 850_000,
      appliedConditions: null,
    })
    const onBought = vi.fn()

    render(<WatchPage onBought={onBought} />)
    await userEvent.click(await screen.findByRole('button', { name: '샀어요' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('제품에 연결되지 않아')
    expect(onBought).not.toHaveBeenCalled()
  })

  /** Q-83 ⑤(2026-07-30 확정) — 부활 미응답 플래그가 서 있으면 안내 + [확인함] 버튼이 뜬다. */
  it('부활 미응답 플래그가 서 있으면 안내와 확인함 버튼을 보여준다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([activeItem({ reviveUnacknowledged: true })])

    render(<WatchPage />)

    expect(await screen.findByText(/다시 살아남/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인함' })).toBeInTheDocument()
  })

  it('플래그가 없으면 안내·확인함 버튼이 안 뜬다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([activeItem({ reviveUnacknowledged: false })])

    render(<WatchPage />)
    await screen.findByText('아이폰 17 특가')

    expect(screen.queryByText(/다시 살아남/)).toBeNull()
    expect(screen.queryByRole('button', { name: '확인함' })).toBeNull()
  })

  it('확인함을 누르면 서버에 확인 처리하고 목록을 다시 불러온다', async () => {
    const list = vi.spyOn(api, 'listActiveWatchItems')
    list
      .mockResolvedValueOnce([activeItem({ reviveUnacknowledged: true })])
      .mockResolvedValueOnce([activeItem({ reviveUnacknowledged: false })])
    const acknowledge = vi.spyOn(api, 'acknowledgeRevival').mockResolvedValue()

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '확인함' }))

    expect(acknowledge).toHaveBeenCalledWith(1)
    expect(await screen.findByText('아이폰 17 특가')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '확인함' })).toBeNull()
  })

  it('기각·해제를 누르면 활성 목록에서 사라진다', async () => {
    const list = vi.spyOn(api, 'listActiveWatchItems')
    list.mockResolvedValueOnce([activeItem()]).mockResolvedValueOnce([])
    const drop = vi.spyOn(api, 'dropWatchItem').mockResolvedValue()

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '기각·해제' }))

    expect(drop).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/핀한 딜이 없습니다/)).toBeInTheDocument()
  })

  it('불러오지 못하면 code를 그대로 보여준다', async () => {
    vi.spyOn(api, 'listActiveWatchItems').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))

    render(<WatchPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP_500')
  })
})

describe('WatchPage — 회고 탭', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    vi.spyOn(api, 'listActiveWatchItems').mockResolvedValue([])
  })

  it('회고 탭으로 가면 결말난 핀을 보여준다', async () => {
    vi.spyOn(api, 'listResolvedWatchItems').mockResolvedValue([
      activeItem({ state: 'BOUGHT', resolvedAt: '2026-07-05T00:00:00Z' }),
    ])

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '회고' }))

    expect(await screen.findByText('아이폰 17 특가')).toBeInTheDocument()
    expect(screen.getByText('샀어요')).toBeInTheDocument()
    expect(screen.getByText('2026-07-05 결말')).toBeInTheDocument()
  })

  it('회고 탭엔 결말 버튼이 없다 — 이미 끝난 일이다', async () => {
    vi.spyOn(api, 'listResolvedWatchItems').mockResolvedValue([
      activeItem({ state: 'MISSED', resolvedAt: '2026-07-05T00:00:00Z' }),
    ])

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '회고' }))
    await screen.findByText('아이폰 17 특가')

    expect(screen.queryByRole('button', { name: '샀어요' })).toBeNull()
    expect(screen.queryByRole('button', { name: '기각·해제' })).toBeNull()
  })

  it('회고가 비면 그 사실을 말한다', async () => {
    vi.spyOn(api, 'listResolvedWatchItems').mockResolvedValue([])

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '회고' }))

    expect(await screen.findByText(/결말난 핀이 없습니다/)).toBeInTheDocument()
  })
})
