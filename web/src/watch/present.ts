/**
 * WATCH(docs/17) 핀 → 화면 문구. **순수 함수.**
 */
import type { WatchItemView } from '../api/types'
import { kstDate } from '../shared/kst'

const won = (amount: number) => `${amount.toLocaleString('en-US')}원`

/** 딜이 사라졌으면(있을 수 없지만 core가 방어적으로 null을 낸다) 지어내지 않고 그 사실을 말한다. */
export function priceLine(item: WatchItemView): string {
  return item.currentPriceLast === null ? '현재가 미확인' : `현재가 ${won(item.currentPriceLast)}`
}

const STATE_LABELS: Record<WatchItemView['state'], string> = {
  ACTIVE: '관찰 중',
  BOUGHT: '샀어요',
  MISSED: '놓쳤어요(종료됨)',
  DROPPED: '기각·해제',
}

export function stateLabel(state: WatchItemView['state']): string {
  return STATE_LABELS[state]
}

/** 활성 핀은 핀한 날을, 결말난 핀은 결말난 날을 보여준다 — "언제부터"와 "언제까지"는 다른 정보다. */
export function dateLine(item: WatchItemView): string {
  if (item.resolvedAt) {
    return `${kstDate(item.resolvedAt)} 결말`
  }
  return `${kstDate(item.pinnedAt)} 핀`
}
