import { describe, expect, it } from 'vitest'
import { InvalidForm } from '../registration/buildCommand'
import { buildPolicyCommand, type PolicyForm } from './buildPolicyCommand'

const EMPTY: PolicyForm = {
  targetPrice: '',
  periodMonths: '6',
  quietHoursStart: '',
  quietHoursEnd: '',
  kDisplay: '5',
  excludeKeywords: '',
}

describe('buildPolicyCommand', () => {
  it('빈 목표가는 0이 아니라 부재다', () => {
    // 0을 보내면 core는 "공짜여야 알림"으로 읽고 400을 준다. 빈 칸은 "목표가 없음"이다.
    expect(buildPolicyCommand(EMPTY)).toEqual({
      targetPrice: null,
      periodMonths: 6,
      quietHoursStart: null,
      quietHoursEnd: null,
      kDisplay: 5,
      excludeKeywords: [],
    })
  })

  it('제외 키워드는 쉼표로 나누고 공백 제거·빈 값 탈락·중복 접기를 한다', () => {
    // core도 다시 정규화하지만(진실은 서버) 여기서 접어 두면 저장 직후 자기가 넣은 그대로를 본다.
    expect(buildPolicyCommand({ ...EMPTY, excludeKeywords: '  리퍼 , 벌크,리퍼, ,해외 ' }).excludeKeywords).toEqual([
      '리퍼',
      '벌크',
      '해외',
    ])
  })

  it('제외 키워드가 비면 빈 배열이다 (키를 빼지 않는다 — PUT은 전체 교체)', () => {
    expect(buildPolicyCommand(EMPTY).excludeKeywords).toEqual([])
  })

  it('천단위 쉼표를 받아들인다', () => {
    expect(buildPolicyCommand({ ...EMPTY, targetPrice: '1,050,000' }).targetPrice).toBe(1_050_000)
  })

  it.each(['0', '-1', 'abc', '90만'])('목표가 %s 는 거절한다', (targetPrice) => {
    expect(() => buildPolicyCommand({ ...EMPTY, targetPrice })).toThrow(InvalidForm)
  })

  it('방해금지는 둘 다 채우거나 둘 다 비운다', () => {
    expect(() => buildPolicyCommand({ ...EMPTY, quietHoursStart: '23' })).toThrow(InvalidForm)
    expect(() => buildPolicyCommand({ ...EMPTY, quietHoursEnd: '8' })).toThrow(InvalidForm)
  })

  it('방해금지 0시는 유효한 값이다 — 빈 칸과 다르다', () => {
    const command = buildPolicyCommand({ ...EMPTY, quietHoursStart: '0', quietHoursEnd: '8' })

    expect(command.quietHoursStart).toBe(0)
    expect(command.quietHoursEnd).toBe(8)
  })

  it.each(['24', '-1', '', ' '])('방해금지 시각 %s 는 하루의 시각이 아니다', (hour) => {
    expect(() => buildPolicyCommand({ ...EMPTY, quietHoursStart: hour, quietHoursEnd: '8' })).toThrow(InvalidForm)
  })

  it('자정을 넘는 구간(23~8)은 정상이다', () => {
    const command = buildPolicyCommand({ ...EMPTY, quietHoursStart: '23', quietHoursEnd: '8' })

    expect(command).toMatchObject({ quietHoursStart: 23, quietHoursEnd: 8 })
  })
})
