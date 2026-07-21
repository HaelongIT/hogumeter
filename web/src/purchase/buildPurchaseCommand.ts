import type { RecordPurchaseCommand } from '../api/types'
import { InvalidForm } from '../registration/buildCommand'

export interface PurchaseForm {
  variantId: number | null
  paidPrice: string
  purchasedDate: string
  observationDays: string
  /** 분리 제품이면 판단 화면에서 고른 값, 묶음이면 null(Q-66 ③). 자유 입력이 아니라 선택된 값을 그대로 받는다. */
  demandAxisValue: string | null
}

/**
 * 날짜만 아는 구매의 발생 시각. V2 마이그레이션 주석이 못박은 계약:
 * "purchased_at ... 날짜만이면 23:59 KST는 **입력 계층**".
 *
 * 오프셋을 문자열에 박아 파싱한다 — `new Date('2026-07-01')`은 UTC 자정으로,
 * `new Date('2026-07-01T23:59')`는 **실행 머신의 타임존**으로 해석된다. 둘 다 틀린다.
 */
const KST_END_OF_DAY = 'T23:59:00+09:00'

const digits = /^\d+$/

export function buildPurchaseCommand(form: PurchaseForm): RecordPurchaseCommand {
  if (form.variantId === null) throw new InvalidForm('구매한 variant를 고르세요')

  const paid = form.paidPrice.replace(/,/g, '').trim()
  if (!digits.test(paid) || Number(paid) <= 0) {
    throw new InvalidForm('실지불가를 0보다 큰 숫자로 입력하세요 (배송비 포함)')
  }

  if (!form.purchasedDate) throw new InvalidForm('구매일을 입력하세요')

  const observationDays = form.observationDays.trim()
  if (observationDays !== '' && (!digits.test(observationDays) || Number(observationDays) <= 0)) {
    throw new InvalidForm('관찰 기간은 1일 이상의 숫자로 입력하세요 (비우면 90일)')
  }

  return {
    variantId: form.variantId,
    paidPrice: Number(paid),
    purchasedAt: new Date(`${form.purchasedDate}${KST_END_OF_DAY}`).toISOString(),
    observationDays: observationDays === '' ? null : Number(observationDays),
    demandAxisValue: form.demandAxisValue,
    // 화면에 딜 연결 입력이 없다. 없는 값을 지어내지 않는다(PUR-01에서 선택 필드).
    linkedDealEventId: null,
  }
}
