import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { PrioritizedProduct } from '../api/types'
import { rankLabel, statusBadge } from './present'

const describeLoad = (failure: unknown) =>
  failure instanceof ApiFailure
    ? `목록을 불러오지 못했습니다 (${failure.code})`
    : '목록을 불러오지 못했습니다 — core가 떠 있는지 확인하세요.'

const describeSave = (failure: unknown) =>
  failure instanceof ApiFailure ? `저장하지 못했습니다 (${failure.code})` : '저장하지 못했습니다 — 알 수 없는 오류'

/** 빈 문자열 = 순번 해제(null). 숫자가 아니면 저장을 막는다(과대약속 금지 — 지어낸 값을 보내지 않는다). */
function parseRank(input: string): number | null | undefined {
  if (input.trim() === '') return null
  const n = Number(input)
  return Number.isInteger(n) ? n : undefined
}

/**
 * PRI(docs/19) 우선순위 — 목록 정렬 표면 1곳. 백엔드가 이미 정렬해서 낸다(대기 중 먼저, 순번 순 —
 * 미지정은 뒤로. 그 뒤에 비대기). 여기서 다시 정렬하지 않는다 — 정렬 규칙의 정본은 core
 * `GetPrioritizedProductsUseCase.list()` 하나다.
 */
export function PriorityPage() {
  const [items, setItems] = useState<PrioritizedProduct[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [drafts, setDrafts] = useState<Record<number, string>>({})
  const [busy, setBusy] = useState<number | null>(null)

  /** 각 행의 순번 입력칸을 서버 값으로 되맞춘다 — 저장 뒤 재조회에서도 재사용. */
  function applyLoaded(loaded: PrioritizedProduct[]) {
    setItems(loaded)
    setDrafts(Object.fromEntries(loaded.map((p) => [p.productId, p.priorityRank?.toString() ?? ''])))
  }

  useEffect(() => {
    let live = true
    api
      .listPrioritizedProducts()
      .then((loaded) => live && applyLoaded(loaded))
      .catch((failure) => live && setError(describeLoad(failure)))
    return () => {
      live = false
    }
  }, [])

  const reload = () => api.listPrioritizedProducts().then(applyLoaded)

  async function saveRank(productId: number) {
    const rank = parseRank(drafts[productId] ?? '')
    if (rank === undefined) {
      setSaveError('순번은 숫자여야 합니다.')
      return
    }
    setBusy(productId)
    setSaveError(null)
    try {
      await api.setPriority(productId, { rank })
      await reload()
    } catch (failure) {
      setSaveError(describeSave(failure))
    } finally {
      setBusy(null)
    }
  }

  async function toggleManuallyCompleted(product: PrioritizedProduct) {
    setBusy(product.productId)
    setSaveError(null)
    try {
      await api.setManuallyCompleted(product.productId, { manuallyCompleted: !product.manuallyCompleted })
      await reload()
    } catch (failure) {
      setSaveError(describeSave(failure))
    } finally {
      setBusy(null)
    }
  }

  return (
    <main>
      <h1>우선순위</h1>

      <p role="note">
        어느 것부터 살지 순서를 상기하는 목록입니다. 대기 중(구매 전)인 제품이 순번 순으로 먼저 나오고,
        순번을 안 정하면 맨 뒤로 갑니다. 이미 산 제품이나 더는 볼 필요 없는 제품은 <strong>수동 완료</strong>로
        표시해 아래로 내리세요(취소 가능).
      </p>

      {error && <p role="alert">{error}</p>}
      {saveError && <p role="alert">{saveError}</p>}
      {!error && items === null && <p className="loading">불러오는 중…</p>}
      {items !== null && items.length === 0 && <p className="empty">등록된 제품이 없습니다.</p>}

      {items !== null && items.length > 0 && (
        <ul aria-label="우선순위 목록">
          {items.map((product) => {
            const badge = statusBadge(product)
            return (
              <li key={product.productId} className="priority-item">
                <span className="priority-name">{product.name}</span>
                <span className="priority-rank">{rankLabel(product)}</span>
                {badge && <span className="priority-badge">{badge}</span>}

                <label>
                  순번 입력
                  <input
                    aria-label="순번 입력"
                    inputMode="numeric"
                    value={drafts[product.productId] ?? ''}
                    disabled={busy !== null}
                    onChange={(event) =>
                      setDrafts((prev) => ({ ...prev, [product.productId]: event.target.value }))
                    }
                  />
                </label>
                <button type="button" disabled={busy !== null} onClick={() => saveRank(product.productId)}>
                  순번 저장
                </button>

                <button type="button" disabled={busy !== null} onClick={() => toggleManuallyCompleted(product)}>
                  {product.manuallyCompleted ? '완료 해제' : '수동 완료로 표시'}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
