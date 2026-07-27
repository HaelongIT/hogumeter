/**
 * PRI(docs/19) 우선순위 목록 → 화면 문구. **순수 함수.**
 *
 * 데이터 진실은 `waiting` 하나뿐이다(core `PriorityQueue.isWaiting`) — CLOSED·ARCHIVED·OBSERVING을
 * 가르지 않는다. 비대기 배지 문구("구매됨/완료")는 표시 손잡이라 여기서 짓는다(docs/91 Q-82).
 */
import type { PrioritizedProduct } from '../api/types'

/** 대기 중이면 배지 없음(줄만 그린다). 아니면 "구매됨/완료" — 이 이상 세분화할 데이터가 아직 없다. */
export function statusBadge(product: PrioritizedProduct): string | null {
  return product.waiting ? null : '구매됨/완료'
}

/** 순번 미지정은 숫자가 아니라 그 사실을 말한다 — 0이나 빈 칸은 "0순위"로 오인될 수 있다. */
export function rankLabel(product: PrioritizedProduct): string {
  return product.priorityRank === null ? '순번 미지정' : `${product.priorityRank}순위`
}
