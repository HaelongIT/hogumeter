/**
 * PUR-05 관찰 문맥 → 화면 한 줄. 순수 함수 — 정직성 규칙은 렌더링이 아니라 테스트가 지킨다.
 *
 * 세 모드는 배타이고 모드 밖 필드는 `null`이다(core `ObservationContext`의 도메인 계약).
 * 그래서 여기서 `null`을 만나면 "0"으로 읽지 않고 **그 모드가 아니라고** 읽는다.
 */
import type { ObservationContext, PurchaseObservation, PurchaseState } from '../api/types'

/** KST는 UTC+9, DST가 없다. 그래서 오프셋 하나로 족하다. */
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

/**
 * Instant(UTC ISO) → **KST 날짜** `YYYY-MM-DD` (OPS-03: 저장 UTC, 표시 KST).
 *
 * ISO 문자열을 `slice(0, 10)`으로 자르면 **UTC 날짜**가 나온다. `2026-07-01T20:00:00Z`는
 * 한국에서 이미 7월 2일 새벽 5시인데 화면엔 7월 1일이 뜬다. 하루가 통째로 어긋난다.
 * `toLocaleDateString`은 실행 머신의 로케일에 따라 형식이 달라지므로 쓰지 않는다.
 */
export function kstDate(instant: string): string {
  return new Date(new Date(instant).getTime() + KST_OFFSET_MS).toISOString().slice(0, 10)
}

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

const pct = (value: number) => `${value > 0 ? '+' : ''}${value.toFixed(1)}%`

const STATE_LABELS: Record<PurchaseState, string> = {
  OBSERVING: '관찰 중',
  REPORT_PENDING: '관찰 종료(성적표 미발급)',
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
      // "집계 중"은 진행 중이라는 뜻이다. 성적표를 발급하는 코드가 없어(docs/91 Q-62) 여기서 영원히
      // 멈춘다 — 기다리면 나온다고 믿게 두지 않는다(과대약속 금지, 절대 원칙 6).
      return '관찰 종료 · 성적표는 아직 발급되지 않습니다'
    case 'ACTIVE_DEAL':
      return activeDealLine(context)
    case 'NO_ACTIVE_DEAL':
      // 0건도 적는다 — "놓친 기회가 없었다"는 그 자체로 정보다.
      return `활성 딜 없음 · 관찰 ${context.observationDay}일차 · 더 싼 기회 ${context.cheaperChanceCount}건`
  }
}
