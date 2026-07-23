/**
 * 도메인 응답 → 화면 문구. **순수 함수** — 여기서 정직성(절대 원칙 1·6)을 강제한다.
 *
 * 규칙 셋:
 *  1. 표본이 빈약하면(SPARSE/NONE) 통계 용어도 금액도 내지 않는다.
 *  2. 현재가 미확립(null)이면 갭을 그리지 않는다 — core가 갭을 아예 계산하지 않는다.
 *  3. 주기는 발생·간격·경과일만 말한다. "다음 딜 예상"은 만들지 않는다.
 */
import type { BenchmarkView, CadenceView, CoupangLatestPrice, SignalColor, SignalView } from '../api/types'

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

/**
 * CMP-01 재료 — 크롬 확장이 보낸 쿠팡 최신 관측가. 관측이 없으면(확장 미연동) 그 사실을 말한다 —
 * 지어내지 않는다(절대 원칙 6). 와우가·배송비는 있을 때만 병기한다(일반 회원은 와우가가 없을 수 있다).
 */
export function coupangPriceLine(price: CoupangLatestPrice): string {
  if (price.regularPrice === null) {
    return '쿠팡 관측가 미확인 — 크롬 확장이 아직 연동되지 않았습니다'
  }
  const wow = price.wowPrice === null ? '' : ` · 와우가 ${won(price.wowPrice)}`
  const shipping = price.shippingFee === null ? '' : ` · 배송비 ${won(price.shippingFee)}`
  const observed = price.observedAt === null ? '' : ` (관측 ${price.observedAt.slice(0, 10)})`
  return `쿠팡 정가 ${won(price.regularPrice)}${wow}${shipping}${observed}`
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
  text: string
  notes: string[]
}

/**
 * 판정 문구 = **답 그 자체**. 화면은 이걸 헤드라인으로 크게 띄운다.
 * 신호색은 램프가 든다(`app.css`) — 예전엔 이모지(🟢)를 같이 냈으나, 램프를 토큰 색으로 그리면서
 * 그 이모지를 아무도 읽지 않게 됐다(이모지 초록은 `--sig-green`이 아니다). 죽은 필드는 남기지 않는다.
 */
const BADGES: Record<SignalColor, string> = {
  GREEN: '지금 잡을 딜 있음',
  YELLOW: '기준가 아래 딜 있음',
  RED: '지금은 잡을 딜 없음',
  GRAY: '표본 부족 — 판단 보류',
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
  const text = BADGES[signal.color]
  // core가 딱지를 붙였으면 그대로 쓰고, 안 붙였는데 굿딜라인이 없으면 우리가 밝힌다.
  const notes =
    signal.goodDealLineEstablished || signal.notes.includes('굿딜라인 미확립')
      ? signal.notes
      : [...signal.notes, '굿딜라인 미확립']

  return { text, notes }
}

/**
 * 히어로의 결정적 한 줄 — 답 바로 아래에서 "왜 그런가"를 한 문장으로.
 *
 * **금액은 기준가가 설 때만 말한다.** SPARSE/NONE이면 `gap.vsBenchmark`가 null이라 여기서 금액이 나갈 수
 * 없다(절대 원칙 1 — 표본이 빈약하면 통계도 금액도 내지 않는다). 현재가 미확립(Q-53)이면 갭 자체가 없다.
 * 원·% 전체는 아래 readouts가 다시 말하므로, 여기선 가장 결정적인 %와 현재가만 든다.
 */
export function verdictSubline(view: BenchmarkView): string {
  if (view.currentPrice === null) {
    return '현재가 미확립 — 갭은 계산하지 않습니다'
  }
  const leg = view.gap.vsBenchmark
  if (leg === null) {
    return '비교할 기준가가 아직 없습니다'
  }
  if (leg.won === 0) {
    return '현재가가 기준가와 같습니다'
  }
  const direction = leg.won > 0 ? '비쌈' : '쌈'
  return `기준가보다 ${Math.abs(leg.pct).toFixed(1)}% ${direction} · 현재가 ${won(view.currentPrice)}`
}
