import { describe, expect, it } from 'vitest'
import { buildCommand, InvalidForm, type RegistrationForm } from './buildCommand'

const form = (overrides: Partial<RegistrationForm> = {}): RegistrationForm => ({
  name: '아이폰 17',
  category: 'phone',
  axisName: '용량',
  axisValues: '256GB, 512GB',
  aliases: '아이폰17, iphone17',
  demandAxisMode: 'GROUPED',
  ...overrides,
})

describe('buildCommand', () => {
  it('축 값 조합대로 variant를 만든다 (REG-02)', () => {
    const command = buildCommand(form())

    expect(command.variants).toEqual([
      { label: '256GB', priceAxisValues: { 용량: '256GB' } },
      { label: '512GB', priceAxisValues: { 용량: '512GB' } },
    ])
    expect(command.axes).toEqual([{ axisType: 'PRICE', name: '용량', allowedValues: ['256GB', '512GB'] }])
  })

  it('쉼표든 줄바꿈이든 목록으로 받는다', () => {
    const command = buildCommand(form({ axisValues: '256GB\n512GB\n' }))

    expect(command.axes[0]!.allowedValues).toEqual(['256GB', '512GB'])
  })

  it('중복 축 값은 접는다 — 같은 variant가 두 번 생기면 안 된다', () => {
    const command = buildCommand(form({ axisValues: '256GB, 256GB, 512GB' }))

    expect(command.variants).toHaveLength(2)
  })

  it('공백을 다듬는다', () => {
    const command = buildCommand(form({ name: '  아이폰 17  ', aliases: ' 아이폰17 ,, ' }))

    expect(command.name).toBe('아이폰 17')
    expect(command.aliases).toEqual(['아이폰17'])
  })

  it('별칭은 비어도 된다 (매칭 사전 시드는 선택)', () => {
    expect(buildCommand(form({ aliases: '' })).aliases).toEqual([])
  })

  it('demandAxisMode는 기본 GROUPED, 토글로 SPLIT (REG-02)', () => {
    expect(buildCommand(form({ demandAxisMode: 'SPLIT' })).demandAxisMode).toBe('SPLIT')
  })

  it.each([
    ['제품명이 비면', { name: '   ' }],
    ['축 이름이 비면', { axisName: '' }],
    ['축 값이 하나도 없으면', { axisValues: ' , , ' }],
  ])('%s 거부한다', (_label, overrides) => {
    expect(() => buildCommand(form(overrides))).toThrow(InvalidForm)
  })
})
