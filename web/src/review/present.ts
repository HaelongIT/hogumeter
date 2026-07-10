/**
 * 미상 큐 항목 → 화면 문구. **순수 함수.**
 *
 * 규칙 둘:
 *  1. **판단하지 않는다**(절대 원칙 2). "사기"·"대박"·"싸다"를 말하지 않는다 — 사실과 원문 링크만 준다.
 *  2. **모르는 것을 숨기지 않는다**(절대 원칙 6). 우리가 모르는 유형이나 빠진 필드가 오면
 *     그 사실을 말하고 근거(payload)를 그대로 내놓는다. `undefined`를 그리지 않는다.
 */
import type { ReviewQueueItem } from '../api/types'

export interface ReviewLine {
  reason: string
  detail: string
}

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

/** payload는 jsonb다 — 기대한 타입이 온다는 보장이 없다. */
const asText = (value: unknown, absent: string) => (typeof value === 'string' && value !== '' ? value : absent)

export function reviewLine(item: ReviewQueueItem): ReviewLine {
  switch (item.type) {
    case 'UNCLASSIFIED': {
      const candidates = Array.isArray(item.payload.productCandidates) ? item.payload.productCandidates : []
      return {
        reason: '미상 — 어느 제품인지 확정하지 못했습니다. 사람이 원문을 보고 정합니다.',
        detail: `${asText(item.payload.title, '제목 없음')} · 후보 ${candidates.length}개`,
      }
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
