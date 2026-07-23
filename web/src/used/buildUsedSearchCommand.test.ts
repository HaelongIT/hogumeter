import { describe, expect, it } from 'vitest'
import { InvalidUsedSearchForm, buildUsedSearchCommand, type UsedSearchForm } from './buildUsedSearchCommand'

const EMPTY: UsedSearchForm = {
  required: '',
  exclude: '',
  bonusGroups: [],
  targetPrice: '',
  pollIntervalMin: '',
}

describe('buildUsedSearchCommand', () => {
  it('필수 키워드가 없으면 거절한다', () => {
    expect(() => buildUsedSearchCommand(EMPTY)).toThrow(InvalidUsedSearchForm)
  })

  it('쉼표·줄바꿈 목록을 배열로 바꾸고 중복을 접는다', () => {
    const cmd = buildUsedSearchCommand({
      ...EMPTY,
      required: '아이폰17, 256\n아이폰17',
      exclude: '파손, 침수',
    })

    expect(cmd.required).toEqual(['아이폰17', '256'])
    expect(cmd.exclude).toEqual(['파손', '침수'])
  })

  it('빈 목표가·주기는 null이다 — 0이 아니다', () => {
    const cmd = buildUsedSearchCommand({ ...EMPTY, required: '아이폰17' })

    expect(cmd.targetPrice).toBeNull()
    expect(cmd.pollIntervalMin).toBeNull()
  })

  it('목표가·주기를 숫자로 파싱한다', () => {
    const cmd = buildUsedSearchCommand({
      ...EMPTY,
      required: '아이폰17',
      targetPrice: '800000',
      pollIntervalMin: '15',
    })

    expect(cmd.targetPrice).toBe(800000)
    expect(cmd.pollIntervalMin).toBe(15)
  })

  it('0 이하 목표가는 거절한다 — 지어내지 않는다', () => {
    expect(() => buildUsedSearchCommand({ ...EMPTY, required: '아이폰17', targetPrice: '0' })).toThrow(
      InvalidUsedSearchForm,
    )
  })

  it('빈 보너스 그룹(키워드 없음)은 걸러진다', () => {
    const cmd = buildUsedSearchCommand({
      ...EMPTY,
      required: '아이폰17',
      bonusGroups: [
        { keywords: '미개봉, 새제품', mode: 'TRIGGER' },
        { keywords: '', mode: 'SORT' },
      ],
    })

    expect(cmd.bonusGroups).toEqual([{ keywords: ['미개봉', '새제품'], mode: 'TRIGGER' }])
  })
})
