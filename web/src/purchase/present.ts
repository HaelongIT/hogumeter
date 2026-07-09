/**
 * PUR-05 관찰 문맥 → 화면 한 줄. 순수 함수 — 정직성 규칙은 렌더링이 아니라 테스트가 지킨다.
 *
 * 세 모드는 배타이고 모드 밖 필드는 `null`이다(core `ObservationContext`의 도메인 계약).
 * 그래서 여기서 `null`을 만나면 "0"으로 읽지 않고 **그 모드가 아니라고** 읽는다.
 */
import type { ObservationContext, PurchaseObservation, PurchaseState } from '../api/types'

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

const pct = (value: number) => `${value > 0 ? '+' : ''}${value.toFixed(1)}%`

const STATE_LABELS: Record<PurchaseState, string> = {
  OBSERVING: '관찰 중',
  REPORT_PENDING: '성적 집계 중',
  CLOSED: '성적표 발급',
  ARCHIVED: '보관',
}

export const stateLabel = (state: PurchaseState) => STATE_LABELS[state]

function activeDealLine(context: ObservationContext): string {
  const lowest = context.activeLowestPriceLast === null ? '' : `활성 딜 최저 ${won(context.activeLowestPriceLast)}`
  if (context.overpaidWon === null || context.overpaidPct === null) return lowest

  if (context.overpaidWon === 0) return `${lowest} — 구매가와 같음`

  // 양수 = 내가 더 주고 샀다. 부호를 문장으로 바꾸되 값 자체는 절댓값으로 읽힌다.
  const verdict = context.overpaidWon > 0 ? '더 주고 샀음' : '싸게 샀음'
  return `${lowest} — ${won(Math.abs(context.overpaidWon))} ${verdict} (${pct(context.overpaidPct)})`
}

export function observationLine(observation: PurchaseObservation): string {
  const context = observation.context

  switch (context.mode) {
    case 'REPORT_PENDING':
      return '성적 집계 중'
    case 'ACTIVE_DEAL':
      return activeDealLine(context)
    case 'NO_ACTIVE_DEAL':
      // 0건도 적는다 — "놓친 기회가 없었다"는 그 자체로 정보다.
      return `활성 딜 없음 · 관찰 ${context.observationDay}일차 · 더 싼 기회 ${context.cheaperChanceCount}건`
  }
}
