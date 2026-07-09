import type { RegisterProductCommand } from '../api/types'

/**
 * 폼 입력 → 등록 명령(순수 함수). REG-02: priceAxis 값 조합대로 variant가 생성된다.
 *
 * 축이 하나뿐인 최소 슬라이스다. 축 2개 이상의 조합(용량×색상)은 확정본이 요구하지만
 * 화면 복잡도가 커져 뒤로 미룬다 — docs/91 Q-47.
 */
export interface RegistrationForm {
  name: string
  category: string
  axisName: string
  axisValues: string
  aliases: string
  demandAxisMode: 'GROUPED' | 'SPLIT'
}

export class InvalidForm extends Error {}

export function buildCommand(form: RegistrationForm): RegisterProductCommand {
  const name = form.name.trim()
  if (!name) throw new InvalidForm('제품명을 입력하세요')

  const axisName = form.axisName.trim()
  if (!axisName) throw new InvalidForm('축 이름을 입력하세요 (예: 용량)')

  const allowedValues = splitList(form.axisValues)
  if (allowedValues.length === 0) throw new InvalidForm('축 값을 하나 이상 입력하세요 (예: 256GB, 512GB)')

  return {
    name,
    category: form.category.trim(),
    demandAxisMode: form.demandAxisMode,
    axes: [{ axisType: 'PRICE', name: axisName, allowedValues }],
    // 축 값 하나가 variant 하나. label은 값 그대로 — 축이 하나뿐이라 모호하지 않다.
    variants: allowedValues.map((value) => ({ label: value, priceAxisValues: { [axisName]: value } })),
    aliases: splitList(form.aliases),
  }
}

/** 쉼표·줄바꿈 어느 쪽으로 적어도 받는다. 중복은 접고 순서는 보존한다. */
function splitList(text: string): string[] {
  const items = text
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
  return [...new Set(items)]
}
