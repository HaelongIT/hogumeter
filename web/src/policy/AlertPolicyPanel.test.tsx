import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import { AlertPolicyPanel } from './AlertPolicyPanel'

describe('AlertPolicyPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  /**
   * 미설정을 조용히 "기본값이 적용 중"으로 그리면, 사용자는 목표가 알림이 켜져 있다고 믿는다.
   * 실제로는 `alert_policy` 행이 없어 목표가 트리거가 발화하지 않는다(확정본 §107).
   */
  it('미설정이면 목표가 알림이 발화하지 않는다고 말한다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5 })

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByRole('note', { name: '정책 미설정 안내' })).toHaveTextContent(/목표가.*발화하지 않습니다/)
  })

  /** 미설정일 때 기간 P를 지어내 채우면 그 숫자가 세 번째 사본이 된다(core의 private 상수가 진실). */
  it('미설정이면 판정 기간을 숫자로 지어내지 않는다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5 })

    render(<AlertPolicyPanel variantId={7} />)
    await screen.findByRole('note', { name: '정책 미설정 안내' })

    expect(screen.getByLabelText(/판정 기간/)).toHaveValue('')
  })

  it('저장된 정책을 폼에 채운다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({
      configured: true,
      targetPrice: 900_000,
      periodMonths: 3,
      quietHoursStart: 23,
      quietHoursEnd: 8,
    })

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByLabelText(/목표가/)).toHaveValue('900,000')
    expect(screen.getByLabelText(/판정 기간/)).toHaveValue('3')
    expect(screen.getByLabelText(/방해금지 시작/)).toHaveValue('23')
    expect(screen.queryByRole('note', { name: '정책 미설정 안내' })).toBeNull()
  })

  it('저장하면 부재를 null로 보낸다 (0이 아니라)', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5 })
    const update = vi
      .spyOn(api, 'updateAlertPolicy')
      .mockResolvedValue({ configured: true, targetPrice: 900_000, periodMonths: 6 })

    render(<AlertPolicyPanel variantId={7} />)
    await screen.findByRole('note', { name: '정책 미설정 안내' })

    await userEvent.type(screen.getByLabelText(/목표가/), '900,000')
    await userEvent.selectOptions(screen.getByLabelText(/판정 기간/), '6')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(7, {
        targetPrice: 900_000,
        periodMonths: 6,
        quietHoursStart: null,
        quietHoursEnd: null,
        kDisplay: 5,
      }),
    )
    expect(screen.queryByRole('note', { name: '정책 미설정 안내' })).toBeNull()
  })

  it('폼 오류는 서버에 보내지 않고 그 자리에서 말한다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5 })
    const update = vi.spyOn(api, 'updateAlertPolicy')

    render(<AlertPolicyPanel variantId={7} />)
    await screen.findByRole('note', { name: '정책 미설정 안내' })

    await userEvent.type(screen.getByLabelText(/목표가/), '0')
    await userEvent.selectOptions(screen.getByLabelText(/판정 기간/), '6')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/목표가/)
    expect(update).not.toHaveBeenCalled()
  })

  /** 클라이언트 검증은 방어가 아니라 편의다. 서버가 거절하면 그 코드를 그대로 보여준다. */
  it('서버가 거절하면 도메인 코드를 보여준다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5 })
    vi.spyOn(api, 'updateAlertPolicy').mockRejectedValue(new ApiFailure(400, 'REG_INVALID_ALERT_POLICY'))

    render(<AlertPolicyPanel variantId={7} />)
    await screen.findByRole('note', { name: '정책 미설정 안내' })

    await userEvent.selectOptions(screen.getByLabelText(/판정 기간/), '6')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('REG_INVALID_ALERT_POLICY')
  })

  it('불러오지 못하면 폼을 그리지 않고 그렇게 말한다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockRejectedValue(new ApiFailure(404, 'BM_VARIANT_NOT_FOUND'))

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByRole('alert')).toHaveTextContent('BM_VARIANT_NOT_FOUND')
    expect(screen.queryByRole('button', { name: '정책 저장' })).toBeNull()
  })

  /**
   * Q-48 ①: K는 사용자 손잡이다. **저장한 K가 그대로 실려야** 한다 — PUT은 전체 교체라, 화면이 K를
   * 안 보내면 core가 기본값(5)으로 되돌린다. 즉 저장할 때마다 사용자가 고른 K가 조용히 리셋된다.
   */
  it('고른 K를 그대로 보낸다 — 안 보내면 저장할 때마다 기본값으로 리셋된다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 8 })
    const update = vi
      .spyOn(api, 'updateAlertPolicy')
      .mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 3 })

    render(<AlertPolicyPanel variantId={7} />)

    // 저장된 K가 폼에 실려 온다(8) — 그대로 저장하면 8이 유지돼야 한다.
    expect(await screen.findByLabelText(/임계 K/)).toHaveValue('8')
    await userEvent.selectOptions(screen.getByLabelText(/임계 K/), '3')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ kDisplay: 3 })))
  })
})
