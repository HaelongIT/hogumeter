import type { RegisterProductCommand, VariantSpec } from '../api/types'

/**
 * 폼 입력 → 등록 명령(순수 함수).
 *
 * REG-02 수용 기준: **priceAxis 값 조합대로 Variant가 생성된다.** 축이 용량(2)×색상(2)이면
 * variant는 4개다. core는 이미 조합을 받는다(`variants[].priceAxisValues` 맵) — UI만 못 따라갔었다.
 */
export interface AxisInput {
  name: string
  /** 쉼표 또는 줄바꿈으로 구분된 값 목록 */
  values: string
}

export interface RegistrationForm {
  name: string
  category: string
  axes: AxisInput[]
  aliases: string
  demandAxisMode: 'GROUPED' | 'SPLIT'
}

export class InvalidForm extends Error {}

export function buildCommand(form: RegistrationForm): RegisterProductCommand {
  const name = form.name.trim()
  if (!name) throw new InvalidForm('제품명을 입력하세요')

  const axes = form.axes.map(normalizeAxis).filter(isFilled)
  if (axes.length === 0) throw new InvalidForm('가격축을 하나 이상 입력하세요 (예: 용량 / 256GB, 512GB)')

  for (const axis of axes) {
    if (!axis.name) throw new InvalidForm('축 이름을 입력하세요 (예: 용량)')
    if (axis.allowedValues.length === 0) throw new InvalidForm(`'${axis.name}' 축의 값을 하나 이상 입력하세요`)
  }

  // 이름이 겹치면 priceAxisValues 맵에서 한 축이 다른 축을 덮어써 variant가 조용히 뭉개진다.
  const names = axes.map((axis) => axis.name)
  if (new Set(names).size !== names.length) throw new InvalidForm('축 이름이 겹칩니다')

  return {
    name,
    category: form.category.trim(),
    demandAxisMode: form.demandAxisMode,
    axes: axes.map((axis) => ({ axisType: 'PRICE' as const, name: axis.name, allowedValues: axis.allowedValues })),
    variants: combine(axes),
    aliases: splitList(form.aliases),
  }
}

interface NormalizedAxis {
  name: string
  allowedValues: string[]
}

function normalizeAxis(axis: AxisInput): NormalizedAxis {
  return { name: axis.name.trim(), allowedValues: splitList(axis.values) }
}

/** 사용자가 축 행을 추가했다가 안 채울 수 있다. 완전히 빈 행은 없는 셈 친다. */
function isFilled(axis: NormalizedAxis): boolean {
  return axis.name !== '' || axis.allowedValues.length > 0
}

/** 축 값들의 데카르트 곱. 앞 축이 바깥 루프 — 사람이 읽는 순서와 같다. */
function combine(axes: NormalizedAxis[]): VariantSpec[] {
  let combos: Array<Record<string, string>> = [{}]
  for (const axis of axes) {
    combos = combos.flatMap((combo) => axis.allowedValues.map((value) => ({ ...combo, [axis.name]: value })))
  }
  return combos.map((priceAxisValues) => ({
    label: Object.values(priceAxisValues).join(' / '),
    priceAxisValues,
  }))
}

/** 쉼표·줄바꿈 어느 쪽으로 적어도 받는다. 중복은 접고 순서는 보존한다. */
function splitList(text: string): string[] {
  const items = text
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
  return [...new Set(items)]
}
