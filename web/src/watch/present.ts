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

/** Q-83 ⑤ — 부활 미응답 플래그. 서 있지 않으면 null(지어내지 않는다) — [확인함] 버튼을 그릴지 화면이 판단. */
export function revivalNotice(item: WatchItemView): string | null {
  return item.reviveUnacknowledged ? '↩️ 다시 살아남 — 아직 확인 안 함' : null
}

/**
 * 지켜보던 딜이 이미 종료됐으면(품절·삭제 등) 그 사실을 말한다 — 활성 탭에 그대로 있어도 [샀어요]가
 * 이미 늦었을 수 있다. `NEW`·`ACTIVE`·`VERIFIED`는 아직 살아있다는 뜻이라 안내하지 않는다. 딜 자체가
 * 사라져 상태를 모르면(방어적 null, `priceLine`이 이미 그 경우를 "현재가 미확인"으로 말한다) 지어내지 않는다.
 */
export function endedNotice(item: WatchItemView): string | null {
  return item.dealStatus === 'ENDED' ? '⛔ 이 딜은 이미 종료됐습니다' : null
}
