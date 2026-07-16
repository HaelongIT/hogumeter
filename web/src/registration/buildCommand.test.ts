import { describe, expect, it } from 'vitest'
import { buildCommand, InvalidForm, type RegistrationForm } from './buildCommand'

const form = (overrides: Partial<RegistrationForm> = {}): RegistrationForm => ({
  name: '아이폰 17',
  category: 'phone',
  axes: [{ name: '용량', values: '256GB, 512GB', type: 'PRICE' }],
  aliases: '아이폰17, iphone17',
  demandAxisMode: 'GROUPED',
  ...overrides,
})

describe('buildCommand', () => {
  it('축이 하나면 값마다 variant 하나', () => {
    const command = buildCommand(form())

    expect(command.variants).toEqual([
      { label: '256GB', priceAxisValues: { 용량: '256GB' } },
      { label: '512GB', priceAxisValues: { 용량: '512GB' } },
    ])
    expect(command.axes).toEqual([{ axisType: 'PRICE', name: '용량', allowedValues: ['256GB', '512GB'] }])
  })

  it('축이 둘이면 조합(데카르트 곱)대로 variant를 만든다 (REG-02 수용 기준)', () => {
    const command = buildCommand(
      form({
        axes: [
          { name: '용량', values: '256GB, 512GB', type: 'PRICE' },
          { name: '색상', values: '블랙, 화이트', type: 'PRICE' },
        ],
      }),
    )

    expect(command.variants).toEqual([
      { label: '256GB / 블랙', priceAxisValues: { 용량: '256GB', 색상: '블랙' } },
      { label: '256GB / 화이트', priceAxisValues: { 용량: '256GB', 색상: '화이트' } },
      { label: '512GB / 블랙', priceAxisValues: { 용량: '512GB', 색상: '블랙' } },
      { label: '512GB / 화이트', priceAxisValues: { 용량: '512GB', 색상: '화이트' } },
    ])
    expect(command.axes).toHaveLength(2)
  })

  it('축 셋도 조합한다', () => {
    const command = buildCommand(
      form({
        axes: [
          { name: 'a', values: '1, 2', type: 'PRICE' },
          { name: 'b', values: 'x, y', type: 'PRICE' },
          { name: 'c', values: 'ㄱ, ㄴ', type: 'PRICE' },
        ],
      }),
    )

    expect(command.variants).toHaveLength(8)
    expect(command.variants[0]!.priceAxisValues).toEqual({ a: '1', b: 'x', c: 'ㄱ' })
  })

  it('쉼표든 줄바꿈이든 목록으로 받는다', () => {
    const command = buildCommand(form({ axes: [{ name: '용량', values: '256GB\n512GB\n', type: 'PRICE' }] }))

    expect(command.axes[0]!.allowedValues).toEqual(['256GB', '512GB'])
  })

  it('중복 축 값은 접는다 — 같은 variant가 두 번 생기면 안 된다', () => {
    const command = buildCommand(form({ axes: [{ name: '용량', values: '256GB, 256GB, 512GB', type: 'PRICE' }] }))

    expect(command.variants).toHaveLength(2)
  })

  it('빈 축 행은 무시한다 — 사용자가 축을 추가했다가 안 채울 수 있다', () => {
    const command = buildCommand(
      form({
        axes: [
          { name: '용량', values: '256GB', type: 'PRICE' },
          { name: '', values: '', type: 'PRICE' },
        ],
      }),
    )

    expect(command.axes).toHaveLength(1)
    expect(command.variants).toHaveLength(1)
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

  it('축 이름이 겹치면 거부한다 — priceAxisValues 맵이 한 값을 덮어쓴다', () => {
    expect(() =>
      buildCommand(
        form({
          axes: [
            { name: '용량', values: '256GB', type: 'PRICE' },
            { name: '용량', values: '512GB', type: 'PRICE' },
          ],
        }),
      ),
    ).toThrow(InvalidForm)
  })

  it.each([
    ['제품명이 비면', { name: '   ' }],
    ['축이 하나도 없으면', { axes: [] }],
    ['축에 값이 없으면', { axes: [{ name: '용량', values: ' , ', type: 'PRICE' as const }] }],
    ['값만 있고 이름이 없으면', { axes: [{ name: ' ', values: '256GB', type: 'PRICE' as const }] }],
  ])('%s 거부한다', (_label, overrides) => {
    expect(() => buildCommand(form(overrides))).toThrow(InvalidForm)
  })

  /**
   * Q-66 ②: 축은 두 종류다(확정본 §38). 예전엔 **모든 축을 가격축으로** 보내 색상 같은 축이 variant를
   * 곱했고, 그만큼 표본이 쪼개져 기준가가 이유 없이 빈약해졌다(n이 작아져 SPARSE).
   */
  describe('수요축은 variant를 나누지 않는다', () => {
    it('색상을 수요축으로 두면 variant가 곱해지지 않는다 — 표본이 쪼개지지 않는다', () => {
      const command = buildCommand(
        form({
          axes: [
            { name: '용량', values: '256GB, 512GB', type: 'PRICE' },
            { name: '색상', values: '블랙, 화이트', type: 'DEMAND' },
          ],
        }),
      )

      // 가격축(2) × 수요축(2) = 4가 아니라, 가격축만으로 2다.
      expect(command.variants).toEqual([
        { label: '256GB', priceAxisValues: { 용량: '256GB' } },
        { label: '512GB', priceAxisValues: { 용량: '512GB' } },
      ])
    })

    it('수요축도 정의는 그대로 보낸다 — 값을 아는 것과 variant를 나누는 것은 다르다', () => {
      const command = buildCommand(
        form({
          axes: [
            { name: '용량', values: '256GB', type: 'PRICE' },
            { name: '색상', values: '블랙, 화이트', type: 'DEMAND' },
          ],
        }),
      )

      expect(command.axes).toEqual([
        { axisType: 'PRICE', name: '용량', allowedValues: ['256GB'] },
        { axisType: 'DEMAND', name: '색상', allowedValues: ['블랙', '화이트'] },
      ])
    })

    it('가격축이 하나도 없으면 거부한다 — variant를 만들 대상이 없다', () => {
      expect(() => buildCommand(form({ axes: [{ name: '색상', values: '블랙', type: 'DEMAND' }] }))).toThrow(
        InvalidForm,
      )
    })
  })
})
