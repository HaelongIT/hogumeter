/**
 * 도메인 응답 → 화면 문구. **순수 함수** — 여기서 정직성(절대 원칙 1·6)을 강제한다.
 *
 * 규칙 셋:
 *  1. 표본이 빈약하면(SPARSE/NONE) 통계 용어도 금액도 내지 않는다.
 *  2. 현재가 미확립(null)이면 갭을 그리지 않는다 — core가 갭을 아예 계산하지 않는다.
 *  3. 주기는 발생·간격·경과일만 말한다. "다음 딜 예상"은 만들지 않는다.
 */
import type { BenchmarkView, CadenceView, SignalColor, SignalView } from '../api/types'

/**
 * 현재가 미확립 판정. core가 네이버 키 미발급 시 `currentPrice: null`을 보낸다(docs/91 Q-53).
 * 해석은 이 한 곳에만 둔다 — 예전엔 sentinel 0이라 `=== 0`이었으나, 이제 null이 타입상 명시라
 * 각 소비처가 `=== null`로 내로잉한다. 실 네이버 어댑터가 붙어도 이 규칙은 그대로 유효하다.
 */
export function currentPriceUnavailable(view: BenchmarkView): boolean {
  return view.currentPrice === null
}

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

const pct = (value: number) => `${value > 0 ? '+' : ''}${value.toFixed(1)}%`

export const sampleLabel = (n: number, m: number) => `${n}건(교차 ${m}건)`

export function benchmarkLine(view: BenchmarkView): string {
  const sample = sampleLabel(view.n, view.m)
  const expanded = view.expandedToMonths ? ` · ${view.expandedToMonths}개월로 확장` : ''

  if (view.tier === 'NONE') {
    return `수집된 딜이 없습니다 — ${sample}`
  }
  if (view.tier === 'SPARSE') {
    return `표본이 적어 기준가를 산출하지 않습니다 — ${sample}, 사례만 나열${expanded}`
  }
  // tier는 SUFFICIENT인데 값이 없다 = 계약 위반. 지어내지 말고 없는 대로 말한다.
  if (view.benchmarkPrice === null) {
    return `기준가를 받지 못했습니다 — ${sample}`
  }
  const goodDeal = view.goodDealLine === null ? '' : ` · 굿딜라인 ${won(view.goodDealLine)}`
  return `핫딜 기준가 ${won(view.benchmarkPrice)}${goodDeal} · ${sample}${expanded}`
}

/**
 * 기간 최저가와 그 날짜. **"기준가보다 비싸다"만으로는 기다릴지 말지 못 정한다** —
 * 이 기간에 얼마까지 내려간 적이 있는지, 그게 언제였는지가 "지금 살까"의 근거다.
 *
 * `periodLowest`는 관측된 사실(실제 딜)이라 표본이 적어도 그대로 말한다. 다만 **현재가 미확립(null)이면
 * 갭을 그리지 않는다**(`gapLine`과 같은 규칙, Q-53).
 *
 * @returns 관측된 최저가가 없으면 `null` — "0원"이나 "최저 없음"을 지어내지 않는다.
 */
export function lowestLine(view: BenchmarkView): string | null {
  const lowest = view.periodLowest
  if (lowest === null) {
    return null
  }
  const observed = `기간 최저 ${won(lowest.price)} (${lowest.date})`

  const leg = view.gap.vsLowest
  if (view.currentPrice === null || leg === null) {
    return observed
  }
  if (leg.won === 0) {
    return `${observed} — 현재가가 기간 최저와 같습니다`
  }
  const direction = leg.won > 0 ? '비쌈' : '쌈'
  return `${observed} — 현재가가 ${won(Math.abs(leg.won))} ${direction} (${pct(leg.pct)})`
}

export function gapLine(view: BenchmarkView): string {
  if (view.currentPrice === null) {
    // core는 미확립을 null로 보내고 갭도 계산하지 않는다(Q-53). 예전엔 0 기준 −100%가 와서 "공짜"로 보였다.
    return '현재가 미확립 — 네이버 쇼핑 API 키가 없어 갭을 계산하지 않습니다 (docs/91 Q-3)'
  }
  const leg = view.gap.vsBenchmark
  if (leg === null) {
    return `현재가 ${won(view.currentPrice)} — 비교할 기준가가 없습니다`
  }
  if (leg.won === 0) {
    return `현재가 ${won(view.currentPrice)} — 기준가와 같습니다`
  }
  const direction = leg.won > 0 ? '비쌈' : '쌈'
  return `현재가 ${won(view.currentPrice)} — 기준가보다 ${won(Math.abs(leg.won))} ${direction} (${pct(leg.pct)})`
}

export function cadenceLine(cadence: CadenceView): string {
  const window = `${cadence.observedMonths}개월`

  if (cadence.eventCount === 0) {
    return `최근 ${window} 발생 없음`
  }
  const elapsed = cadence.elapsedDays === null ? '' : ` · 마지막 딜 ${cadence.elapsedDays}일 전`

  if (!cadence.guardMet || cadence.intervalMedianDays === null) {
    return `${window} ${cadence.eventCount}회 — 주기 판단 불가(표본 부족)${elapsed}`
  }
  return `${window} ${cadence.eventCount}회 · 간격 median ${cadence.intervalMedianDays}일${elapsed}`
}

export interface SignalBadge {
  mark: string
  text: string
  notes: string[]
}

const BADGES: Record<SignalColor, { mark: string; text: string }> = {
  GREEN: { mark: '🟢', text: '지금 잡을 딜 있음' },
  YELLOW: { mark: '🟡', text: '기준가 아래 딜 있음' },
  RED: { mark: '🔴', text: '지금은 잡을 딜 없음' },
  GRAY: { mark: '⚪', text: '표본 부족 — 판단 보류' },
}

/**
 * 사례·최근딜에 붙는 조건 태그(BM-02, Q-46 ①). 없으면 빈 문자열.
 * `카할`이면 특정 카드 보유자만 그 가격이다 — "정상 가격"으로 오인하지 않게 병기한다(절대 원칙 2).
 * 배송비미상은 표본에서 이미 빠지므로(②) 여기 오지 않는다.
 */
export function conditionsSuffix(conditions: string[]): string {
  return conditions.length === 0 ? '' : ` · 조건부: ${conditions.join(' · ')}`
}

export function signalBadge(signal: SignalView): SignalBadge {
  const { mark, text } = BADGES[signal.color]
  // core가 딱지를 붙였으면 그대로 쓰고, 안 붙였는데 굿딜라인이 없으면 우리가 밝힌다.
  const notes =
    signal.goodDealLineEstablished || signal.notes.includes('굿딜라인 미확립')
      ? signal.notes
      : [...signal.notes, '굿딜라인 미확립']

  return { mark, text, notes }
}
