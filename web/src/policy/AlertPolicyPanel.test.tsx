import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import { AlertPolicyPanel } from './AlertPolicyPanel'

describe('AlertPolicyPanel', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    // 기본은 발송됨 — 경고를 그리지 않는다. 개별 테스트가 필요하면 덮는다.
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
  })

  /**
   * 미설정을 조용히 "기본값이 적용 중"으로 그리면, 사용자는 목표가 알림이 켜져 있다고 믿는다.
   * 실제로는 `alert_policy` 행이 없어 목표가 트리거가 발화하지 않는다(확정본 §107).
   */
  it('미설정이면 목표가 알림이 발화하지 않는다고 말한다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByRole('note', { name: '정책 미설정 안내' })).toHaveTextContent(/목표가.*발화하지 않습니다/)
  })

  /** 미설정일 때 기간 P를 지어내 채우면 그 숫자가 세 번째 사본이 된다(core의 private 상수가 진실). */
  it('미설정이면 판정 기간을 숫자로 지어내지 않는다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })

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
      excludeKeywords: [],
    })

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByLabelText(/목표가/)).toHaveValue('900,000')
    expect(screen.getByLabelText(/판정 기간/)).toHaveValue('3')
    expect(screen.getByLabelText(/방해금지 시작/)).toHaveValue('23')
    expect(screen.queryByRole('note', { name: '정책 미설정 안내' })).toBeNull()
  })

  it('저장하면 부재를 null로 보낸다 (0이 아니라)', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })
    const update = vi
      .spyOn(api, 'updateAlertPolicy')
      .mockResolvedValue({ configured: true, targetPrice: 900_000, periodMonths: 6, excludeKeywords: [] })

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
        excludeKeywords: [],
      }),
    )
    expect(screen.queryByRole('note', { name: '정책 미설정 안내' })).toBeNull()
  })

  it('폼 오류는 서버에 보내지 않고 그 자리에서 말한다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })
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
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: false, kDisplay: 5, excludeKeywords: [] })
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
   * 과대약속 금지(절대 원칙 6): 알림이 실제로 안 나가면(스텁) 그 사실을 밝힌다. 목표가만 설정하고
   * 텔레그램이 꺼져 있으면 사용자는 알림이 온다고 믿는데 로그로만 남는다.
   */
  it('알림이 스텁이면(delivering:false) 실제로 안 나간다고 경고한다', async () => {
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: false })
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 5, excludeKeywords: [] })

    render(<AlertPolicyPanel variantId={7} />)

    expect(await screen.findByRole('alert', { name: '알림 미발송 안내' })).toHaveTextContent(/실제로 발송되지 않습니다/)
  })

  it('알림이 실제로 발송되면(delivering:true) 미발송 경고를 그리지 않는다', async () => {
    vi.spyOn(api, 'getAlertStatus').mockResolvedValue({ delivering: true })
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 5, excludeKeywords: [] })

    render(<AlertPolicyPanel variantId={7} />)

    await screen.findByLabelText(/임계 K/)
    expect(screen.queryByRole('alert', { name: '알림 미발송 안내' })).toBeNull()
  })

  /**
   * Q-48 ①: K는 사용자 손잡이다. **저장한 K가 그대로 실려야** 한다 — PUT은 전체 교체라, 화면이 K를
   * 안 보내면 core가 기본값(5)으로 되돌린다. 즉 저장할 때마다 사용자가 고른 K가 조용히 리셋된다.
   */
  it('고른 K를 그대로 보낸다 — 안 보내면 저장할 때마다 기본값으로 리셋된다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({
      configured: true,
      periodMonths: 6,
      kDisplay: 8,
      excludeKeywords: [],
    })
    const update = vi
      .spyOn(api, 'updateAlertPolicy')
      .mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 3, excludeKeywords: [] })

    render(<AlertPolicyPanel variantId={7} />)

    // 저장된 K가 폼에 실려 온다(8) — 그대로 저장하면 8이 유지돼야 한다.
    expect(await screen.findByLabelText(/임계 K/)).toHaveValue('8')
    await userEvent.selectOptions(screen.getByLabelText(/임계 K/), '3')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    await waitFor(() => expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ kDisplay: 3 })))
  })

  /**
   * Q-28: 제외 키워드는 사용자 손잡이다. 저장된 목록이 폼에 쉼표로 실려 오고, 편집한 값이 배열로 나간다.
   * 이 배선이 없으면 `alert_policy.exclude_keywords`는 저장돼도 아무도 쓰지 않는 죽은 컬럼이다.
   */
  it('저장된 제외 키워드를 폼에 채우고, 편집한 값을 배열로 보낸다', async () => {
    vi.spyOn(api, 'getAlertPolicy').mockResolvedValue({
      configured: true,
      periodMonths: 6,
      kDisplay: 5,
      excludeKeywords: ['리퍼', '벌크'],
    })
    const update = vi
      .spyOn(api, 'updateAlertPolicy')
      .mockResolvedValue({ configured: true, periodMonths: 6, kDisplay: 5, excludeKeywords: ['리퍼', '해외'] })

    render(<AlertPolicyPanel variantId={7} />)

    // 저장된 목록이 쉼표로 이어져 폼에 실려 온다.
    const field = await screen.findByLabelText('제외 키워드')
    expect(field).toHaveValue('리퍼, 벌크')

    // 편집: "벌크"를 지우고 "해외"를 넣는다. 중복·공백은 접혀 배열로 나간다.
    await userEvent.clear(field)
    await userEvent.type(field, '리퍼,  해외 , 리퍼')
    await userEvent.click(screen.getByRole('button', { name: '정책 저장' }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ excludeKeywords: ['리퍼', '해외'] })),
    )
  })
})
