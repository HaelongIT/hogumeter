import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api/client'
import { App } from './App'

describe('App', () => {
  beforeEach(() => {
    vi.spyOn(api, 'listProducts').mockResolvedValue([])
  })

  it('판단 화면으로 열린다 — 등록은 한 번 하고 마는 일이다', async () => {
    render(<App />)
    expect(await screen.findByRole('heading', { name: '지금 사도 되나' })).toBeInTheDocument()
  })

  it('탭으로 등록 화면에 간다', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: '제품 등록' }))

    expect(await screen.findByRole('heading', { name: '제품 등록' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '지금 사도 되나' })).not.toBeInTheDocument()
  })
})
