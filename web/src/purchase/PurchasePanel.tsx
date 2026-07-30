import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { PurchaseObservation } from '../api/types'
import { InvalidForm } from '../registration/buildCommand'
import { todayKst } from '../shared/kst'
import { buildPurchaseCommand, type PurchaseForm } from './buildPurchaseCommand'
import { kstDate, observationLine, prefillNotice, reportCardLine, stateLabel } from './present'

const EMPTY = { paidPrice: '', purchasedDate: '', observationDays: '' }

/** WATCH [샀어요] → 판단 화면 이동(Q-83 ②)이 실어 오는 프리필 재료. */
export interface PurchasePrefill {
  dealEventId: number
  dealPrice: number | null
  appliedConditions: string[] | null
}

const describe = (failure: unknown) => {
  if (failure instanceof InvalidForm) return failure.message
  if (failure instanceof ApiFailure) return `구매 기록 실패 (${failure.code})`
  return '구매 기록 실패: 알 수 없는 오류'
}

/**
 * 사후 판정색 근거 — 활성 딜 대비 얼마나 더/덜 냈나(overpaidWon 부호). 표시 색만 정하고 문구는
 * `observationLine`이 낸다. ACTIVE_DEAL·값 있음일 때만 판정한다(다른 모드엔 비교 대상이 없다).
 * 과한 알람을 피해 over=주의(amber), under=안심(green)으로 둔다 — "호구"라 단정하지 않는다(절대 원칙 2).
 */
function verdictOf(purchase: PurchaseObservation): 'over' | 'under' | 'even' | undefined {
  const { mode, overpaidWon } = purchase.context
  if (mode !== 'ACTIVE_DEAL' || overpaidWon === null) return undefined
  if (overpaidWon > 0) return 'over'
  if (overpaidWon < 0) return 'under'
  return 'even'
}

/**
 * PUR — "지금 사도 되나" 바로 아래에 "샀다"를 놓는다. 판단과 기록이 같은 variant 문맥에 있어야
 * 사후에 "호구였나"를 물을 수 있다(docs/15 구매 이후 루프).
 *
 * <p>{@code demandAxisValue}: 분리(SPLIT) 제품이면 판단 화면에서 이미 고른 값을 그대로 받는다(Q-66 ③) —
 * 여기서 자유 입력을 다시 받으면 판단과 다른 색을 적을 수 있고, core는 "SPLIT인데 값 없음"으로 400을 낸다.
 * 묶음(GROUPED) 제품이면 {@code null}이고 이 손잡이 자체를 그리지 않는다.
 *
 * <p>{@code prefill}(Q-83 ②): WATCH [샀어요]로 넘어왔으면 실지불가·구매일·연결 딜을 미리 채운다.
 * **최초 마운트 시 한 번만** 반영한다 — variant를 나중에 바꿔도 이미 채운 값은 유지한다(딜과 무관하게
 * 사람이 고친 값이 됐을 수 있다). 프리필은 lazy 초기값일 뿐이라 이 컴포넌트가 리마운트될 때만 다시 먹는다.
 */
export function PurchasePanel({
  variantId,
  demandAxisValue = null,
  prefill = null,
}: {
  variantId: number
  demandAxisValue?: string | null
  prefill?: PurchasePrefill | null
}) {
  const [form, setForm] = useState(() =>
    prefill
      ? { paidPrice: prefill.dealPrice === null ? '' : String(prefill.dealPrice), purchasedDate: todayKst(), observationDays: '' }
      : EMPTY,
  )
  const [linkedDealEventId, setLinkedDealEventId] = useState<number | null>(prefill?.dealEventId ?? null)
  const [purchases, setPurchases] = useState<PurchaseObservation[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = (id: number) =>
    api
      .listPurchases(id)
      .then(setPurchases)
      .catch(() => setError('구매 기록을 불러오지 못했습니다.'))

  useEffect(() => {
    setPurchases(null)
    setError(null)
    void reload(variantId)
  }, [variantId])

  const set = (key: keyof typeof EMPTY) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const command = buildPurchaseCommand({
        ...form,
        variantId,
        demandAxisValue,
        linkedDealEventId,
      } satisfies PurchaseForm)
      await api.recordPurchase(command)
      setForm(EMPTY)
      setLinkedDealEventId(null) // 프리필은 한 번만 — 다음 기록에 이 딜이 딸려가지 않는다
      await reload(variantId)
    } catch (failure) {
      setError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="구매 기록">
      <h2>샀다면 기록해 두기</h2>

      <form onSubmit={submit} aria-label="구매 기록">
        <label>
          실지불가 (배송비 포함)
          <input value={form.paidPrice} onChange={set('paidPrice')} placeholder="899,000" />
        </label>
        {/* WATCH 핀에서 넘어온 값이면 그 딜의 조건 태그로 안내를 가른다(Q-83 ②) — 늘 뜨는 경고는 안 읽힌다. */}
        {prefill && (
          <p className="purchase-prefill-note" role="note">
            {prefillNotice(prefill.appliedConditions)}
          </p>
        )}
        <label>
          구매일
          <input type="date" value={form.purchasedDate} onChange={set('purchasedDate')} />
        </label>
        <label>
          관찰 기간 (일, 비우면 90)
          <input value={form.observationDays} onChange={set('observationDays')} placeholder="90" />
        </label>
        {/* 분리 제품이면 판단 화면에서 고른 값으로 기록한다 — 여기서 다시 입력받지 않는다(다른 색을 적을 위험). */}
        {demandAxisValue !== null && (
          <p className="purchase-demand" aria-label="수요축 값">
            수요축 값 <strong>{demandAxisValue}</strong> 기준으로 기록합니다 (판단 화면에서 고른 값).
          </p>
        )}
        <button type="submit" disabled={busy}>
          기록
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      {purchases?.length === 0 && <p className="empty">이 variant의 구매 기록이 없습니다.</p>}

      {purchases && purchases.length > 0 && (
        <ul aria-label="구매 목록">
          {purchases.map((purchase) => (
            <li key={purchase.purchaseId} className="purchase-item" data-verdict={verdictOf(purchase)}>
              <p className="purchase-head">
                <span aria-label="구매가">{purchase.paidPrice.toLocaleString('en-US')}원</span>
                <span className="purchase-meta">
                  {kstDate(purchase.purchasedAt)} · <span className="state-chip">{stateLabel(purchase.state)}</span>
                </span>
              </p>
              {/* CLOSED면 발급된 성적표가 관찰 문맥을 대신한다 — 관찰은 끝났고 성적표가 그 요약이다. */}
              {purchase.reportCard ? (
                <span className="purchase-report" aria-label={`성적표 ${purchase.purchaseId}`}>
                  {reportCardLine(purchase.reportCard)}
                </span>
              ) : (
                <span className="purchase-obs" aria-label={`관찰 문맥 ${purchase.purchaseId}`}>
                  {observationLine(purchase)}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
