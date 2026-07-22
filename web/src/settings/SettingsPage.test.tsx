import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, api } from '../api/client'
import { SettingsPage } from './SettingsPage'

describe('SettingsPage — 전역 제외 키워드 (Q-28 ①)', () => {
  beforeEach(() => {
    vi.spyOn(api, 'getGlobalExcludeKeywords').mockResolvedValue({ excludeKeywords: ['리퍼', '중고'] })
    vi.spyOn(api, 'updateGlobalExcludeKeywords').mockResolvedValue({ excludeKeywords: ['리퍼'] })
  })

  it('저장된 전역 키워드를 쉼표 한 줄로 채운다', async () => {
    render(<SettingsPage />)

    await waitFor(() => expect(screen.getByLabelText('전역 제외 키워드 입력')).toHaveValue('리퍼, 중고'))
  })

  it('저장하면 배열로 보내고, core가 정규화한 결과를 화면에 되돌린다', async () => {
    render(<SettingsPage />)
    const input = await screen.findByLabelText('전역 제외 키워드 입력')

    await userEvent.clear(input)
    await userEvent.type(input, ' 리퍼 , , 리퍼')
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    // 보낼 땐 최소 정리(공백·빈 항목 제거), 정규화 정본은 core다
    await waitFor(() => expect(api.updateGlobalExcludeKeywords).toHaveBeenCalledWith(['리퍼', '리퍼']))
    // core가 중복을 접어 돌려준 값이 화면에 반영된다 — 무엇이 실제로 저장됐는지 보여준다
    await waitFor(() => expect(input).toHaveValue('리퍼'))
    expect(await screen.findByRole('status')).toHaveTextContent('저장했습니다')
  })

  it('빈 목록으로 저장하면 "전역 제외 없음"이라고 정직하게 말한다', async () => {
    vi.spyOn(api, 'updateGlobalExcludeKeywords').mockResolvedValue({ excludeKeywords: [] })
    render(<SettingsPage />)
    const input = await screen.findByLabelText('전역 제외 키워드 입력')

    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('status')).toHaveTextContent('전역 제외 없음')
  })

  it('서버 실패는 code를 그대로 보여준다 — 삼키지 않는다', async () => {
    vi.spyOn(api, 'updateGlobalExcludeKeywords').mockRejectedValue(new ApiFailure(500, 'HTTP_500'))
    render(<SettingsPage />)
    await screen.findByLabelText('전역 제외 키워드 입력')

    await userEvent.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('HTTP_500')
  })

  it('제품별 목록과 합쳐 적용된다는 사실을 화면이 말한다 (과대약속 금지)', async () => {
    render(<SettingsPage />)

    const note = await screen.findByLabelText('전역 제외 키워드 안내')
    expect(note).toHaveTextContent('모든 제품')
    expect(note).toHaveTextContent('합쳐서')
  })
})
