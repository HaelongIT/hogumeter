import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { PurchaseObservation } from '../api/types'
import { InvalidForm } from '../registration/buildCommand'
import { buildPurchaseCommand, type PurchaseForm } from './buildPurchaseCommand'
import { kstDate, observationLine, stateLabel } from './present'

const EMPTY = { paidPrice: '', purchasedDate: '', observationDays: '', demandAxisValue: '' }

const describe = (failure: unknown) => {
  if (failure instanceof InvalidForm) return failure.message
  if (failure instanceof ApiFailure) return `구매 기록 실패 (${failure.code})`
  return '구매 기록 실패: 알 수 없는 오류'
}

/**
 * PUR — "지금 사도 되나" 바로 아래에 "샀다"를 놓는다. 판단과 기록이 같은 variant 문맥에 있어야
 * 사후에 "호구였나"를 물을 수 있다(docs/15 구매 이후 루프).
 */
export function PurchasePanel({ variantId }: { variantId: number }) {
  const [form, setForm] = useState(EMPTY)
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
      const command = buildPurchaseCommand({ ...form, variantId } satisfies PurchaseForm)
      await api.recordPurchase(command)
      setForm(EMPTY)
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
        <label>
          구매일
          <input type="date" value={form.purchasedDate} onChange={set('purchasedDate')} />
        </label>
        <label>
          관찰 기간 (일, 비우면 90)
          <input value={form.observationDays} onChange={set('observationDays')} placeholder="90" />
        </label>
        <label>
          수요축 값 (선택)
          <input value={form.demandAxisValue} onChange={set('demandAxisValue')} placeholder="자급제" />
        </label>
        <button type="submit" disabled={busy}>
          기록
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      {purchases?.length === 0 && <p>이 variant의 구매 기록이 없습니다.</p>}

      {purchases && purchases.length > 0 && (
        <ul aria-label="구매 목록">
          {purchases.map((purchase) => (
            <li key={purchase.purchaseId}>
              <span aria-label="구매가">{purchase.paidPrice.toLocaleString('en-US')}원</span> ·{' '}
              {kstDate(purchase.purchasedAt)} · {stateLabel(purchase.state)}
              <br />
              <span aria-label={`관찰 문맥 ${purchase.purchaseId}`}>{observationLine(purchase)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
