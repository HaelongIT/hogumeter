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
    const markBought = vi.spyOn(api, 'markWatchItemBought').mockResolvedValue()

    render(<WatchPage />)
    await userEvent.click(await screen.findByRole('button', { name: '샀어요' }))

    expect(markBought).toHaveBeenCalledWith(1)
    expect(await screen.findByText(/핀한 딜이 없습니다/)).toBeInTheDocument()
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
