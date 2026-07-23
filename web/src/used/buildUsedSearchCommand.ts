import type { BonusMode, RegisterUsedSearchCommand } from '../api/types'

/** 등록 폼 입력 → 명령(순수 함수). registration/buildCommand.ts와 같은 형태. */
export interface BonusGroupForm {
  keywords: string
  mode: BonusMode
}

export interface UsedSearchForm {
  required: string
  exclude: string
  bonusGroups: BonusGroupForm[]
  targetPrice: string
  pollIntervalMin: string
}

export class InvalidUsedSearchForm extends Error {}

export function buildUsedSearchCommand(form: UsedSearchForm): RegisterUsedSearchCommand {
  const required = splitList(form.required)
  if (required.length === 0) throw new InvalidUsedSearchForm('필수 키워드를 하나 이상 입력하세요')

  const bonusGroups = form.bonusGroups
    .map((group) => ({ keywords: splitList(group.keywords), mode: group.mode }))
    .filter((group) => group.keywords.length > 0)

  return {
    required,
    exclude: splitList(form.exclude),
    bonusGroups,
    targetPrice: parseOptionalPositive(form.targetPrice, '목표가'),
    pollIntervalMin: parseOptionalPositive(form.pollIntervalMin, '폴링 주기'),
  }
}

/** 빈 문자열은 "설정 안 함"(null) — 0이나 음수로 지어내지 않는다. */
function parseOptionalPositive(text: string, label: string): number | null {
  const trimmed = text.trim()
  if (trimmed === '') return null
  const value = Number(trimmed)
  if (!Number.isFinite(value) || value <= 0) throw new InvalidUsedSearchForm(`${label}은 0보다 큰 숫자여야 합니다`)
  return value
}

function splitList(text: string): string[] {
  const items = text
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
  return [...new Set(items)]
}
