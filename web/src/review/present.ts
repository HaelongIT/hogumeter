/**
 * 미상 큐 항목 → 화면 문구. **순수 함수.**
 *
 * 규칙 둘:
 *  1. **판단하지 않는다**(절대 원칙 2). "사기"·"대박"·"싸다"를 말하지 않는다 — 사실과 원문 링크만 준다.
 *  2. **모르는 것을 숨기지 않는다**(절대 원칙 6). 우리가 모르는 유형이나 빠진 필드가 오면
 *     그 사실을 말하고 근거(payload)를 그대로 내놓는다. `undefined`를 그리지 않는다.
 */
import type { ReviewQueueItem } from '../api/types'
// KST 해석은 한 곳에서만 한다. 여기서 다시 구현하면 오프셋 계산이 두 벌이 되고, 사본은 드리프트한다.
// 세 번째 소비자가 생기면 공용 모듈로 옮긴다.
import { kstDate } from '../purchase/present'

export interface ReviewLine {
  reason: string
  detail: string
}

/**
 * "언제부터 쌓였나". `occurrences`만으로는 47번이 하루 새 일인지 한 달째인지 알 수 없다 —
 * **구간의 길이가 곧 결함의 나이**다(Q-27 ④: 매 틱 재처리).
 */
export function seenLine(item: ReviewQueueItem): string {
  const first = kstDate(item.firstSeenAt)
  const last = kstDate(item.lastSeenAt)

  if (item.occurrences === 1) {
    return `${first} 접수`
  }
  const span = first === last ? first : `${first} ~ ${last}`
  return `${span} · 같은 항목이 ${item.occurrences}번 다시 쌓였습니다 (수집 파이프라인이 매 주기 재처리합니다)`
}

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

/** payload는 jsonb다 — 기대한 타입이 온다는 보장이 없다. */
const asText = (value: unknown, absent: string) => (typeof value === 'string' && value !== '' ? value : absent)

export function reviewLine(item: ReviewQueueItem): ReviewLine {
  switch (item.type) {
    case 'UNCLASSIFIED':
      return {
        reason: '미상 — 어느 제품인지 확정하지 못했습니다. 사람이 원문을 보고 정합니다.',
        // "후보 2개"는 판단에 아무 도움이 안 된다. **무엇의 후보인지**를 말해야 1초 만에 고른다.
        detail: `${asText(item.payload.title, '제목 없음')} · ${candidateLine(item.candidateProducts)}`,
      }
    case 'OUTLIER_LOWER':
      return {
        reason: '분포 하단 이상치 — 기준가 표본에서 제외됐습니다. 원문을 보고 판단하세요.',
        detail: price(item.payload.priceFirst),
      }
    default:
      // 새 유형이 생겼는데 화면이 모른다. 빈 줄을 그리느니 근거를 통째로 보여준다.
      return {
        reason: `알 수 없는 유형(${item.type}) — 근거를 그대로 표시합니다.`,
        detail: JSON.stringify(item.payload),
      }
  }
}

/** 숫자가 아니면 금액으로 꾸미지 않는다 — 꾸민 값은 사실처럼 보인다. */
function price(value: unknown): string {
  return typeof value === 'number' ? won(value) : String(value)
}

/** 사라진 제품은 core가 `#id`로 보낸다. 그대로 그린다 — 숨기면 근거가 줄어든 걸 아무도 모른다. */
function candidateLine(candidates: string[]): string {
  return candidates.length === 0 ? '후보 없음' : `후보: ${candidates.join(', ')}`
}
