import type { UpdateAlertPolicyCommand } from '../api/types'
import { InvalidForm } from '../registration/buildCommand'

export interface PolicyForm {
  targetPrice: string
  periodMonths: string
  quietHoursStart: string
  quietHoursEnd: string
  /** 기준가 라벨 임계 K(3~10). 항상 보낸다 — PUT은 전체 교체라 빼면 core가 기본값으로 되돌린다. */
  kDisplay: string
  /** 제외 키워드(Q-28). 쉼표로 구분한 한 줄 — 걸리는 딜은 기준가·신호·알림 표본에서 빠진다. 빈 칸이면 없음. */
  excludeKeywords: string
}

const digits = /^\d+$/

/**
 * 폼(문자열) → PUT 본문. **검증은 서버가 진실이고 여기 것은 편의다** — 그래서 규칙을 늘리지 않고,
 * core가 거절하면 그 코드를 그대로 보여준다(`AlertPolicyPanel`).
 *
 * 여기서 반드시 해야 하는 일은 검증이 아니라 <b>변환</b>이다: 빈 칸은 `0`이 아니라 `null`이어야 한다.
 * `0`을 보내면 core는 "공짜여야 알림"으로 읽는다 — 목표가 미설정과 정반대의 뜻이다.
 */
export function buildPolicyCommand(form: PolicyForm): UpdateAlertPolicyCommand {
  const target = form.targetPrice.replace(/,/g, '').trim()
  if (target !== '' && (!digits.test(target) || Number(target) <= 0)) {
    throw new InvalidForm('목표가는 0보다 큰 숫자로 입력하세요 (비우면 목표가 알림 없음)')
  }

  const period = form.periodMonths.trim()
  if (!digits.test(period) || Number(period) <= 0) {
    throw new InvalidForm('알림 판정 기간을 고르세요')
  }

  const start = form.quietHoursStart.trim()
  const end = form.quietHoursEnd.trim()
  if ((start === '') !== (end === '')) {
    // 한쪽만 설정하면 core의 QuietHours가 조용히 "방해금지 없음"으로 읽는다 — 설정한 줄 알고 있는데.
    throw new InvalidForm('방해금지 시간은 시작과 끝을 함께 입력하거나 둘 다 비우세요')
  }

  const k = form.kDisplay.trim()
  if (!digits.test(k) || Number(k) < 3 || Number(k) > 10) {
    throw new InvalidForm('기준가 표시 임계 K는 3~10 사이의 정수여야 합니다')
  }

  return {
    targetPrice: target === '' ? null : Number(target),
    periodMonths: Number(period),
    quietHoursStart: start === '' ? null : hourOfDay(start, '방해금지 시작'),
    quietHoursEnd: end === '' ? null : hourOfDay(end, '방해금지 끝'),
    kDisplay: Number(k),
    excludeKeywords: parseKeywords(form.excludeKeywords),
  }
}

/**
 * 쉼표로 구분한 한 줄 → 키워드 배열. 공백 제거·빈 값 탈락·중복 접기 — core가 다시 정규화하지만
 * 여기서도 접어 두면 사용자가 저장 직후 자기가 넣은 그대로를 본다. 검증이 아니라 편의다(빈 목록도 정상).
 */
function parseKeywords(raw: string): string[] {
  const seen = new Set<string>()
  for (const part of raw.split(',')) {
    const keyword = part.trim()
    if (keyword !== '') seen.add(keyword)
  }
  return [...seen]
}

function hourOfDay(value: string, field: string): number {
  if (!digits.test(value) || Number(value) > 23) {
    throw new InvalidForm(`${field} 시각은 0~23 사이의 정수여야 합니다`)
  }
  return Number(value)
}
