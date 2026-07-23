import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { BonusMode, ProductSummary, UsedSearchView } from '../api/types'
import { InvalidUsedSearchForm, buildUsedSearchCommand, type BonusGroupForm, type UsedSearchForm } from './buildUsedSearchCommand'

const EMPTY: UsedSearchForm = {
  required: '',
  exclude: '',
  bonusGroups: [{ keywords: '', mode: 'TRIGGER' }],
  targetPrice: '',
  pollIntervalMin: '',
}

function describe(failure: unknown): string {
  if (failure instanceof InvalidUsedSearchForm) return failure.message
  if (failure instanceof ApiFailure) return `등록 실패 (${failure.code})`
  return '등록 실패: 알 수 없는 오류'
}

/**
 * USED-01 중고 조건검색 등록 + 조회. 3계층 필터(required AND / bonusGroups OR / exclude NOT)를
 * 사람이 입력하고, 등록된 검색 목록을 다시 볼 수 있다(evaluate·comparison 화면이 이 id를 쓴다).
 */
export function UsedSearchPage({ onRegistered }: { onRegistered?: (productId: number) => void } = {}) {
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [productId, setProductId] = useState<number | null>(null)
  const [form, setForm] = useState<UsedSearchForm>(EMPTY)
  const [searches, setSearches] = useState<UsedSearchView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api
      .listProducts()
      .then(setProducts)
      .catch(() => setError('제품 목록을 불러오지 못했습니다.'))
  }, [])

  useEffect(() => {
    if (productId === null) {
      setSearches(null)
      return
    }
    let live = true
    api
      .listUsedSearches(productId)
      .then((loaded) => live && setSearches(loaded))
      .catch(() => live && setError('중고 검색 목록을 불러오지 못했습니다.'))
    return () => {
      live = false
    }
  }, [productId])

  const setBonusGroup = (index: number, patch: Partial<BonusGroupForm>) =>
    setForm((current) => ({
      ...current,
      bonusGroups: current.bonusGroups.map((group, i) => (i === index ? { ...group, ...patch } : group)),
    }))

  const addBonusGroup = () =>
    setForm((current) => ({ ...current, bonusGroups: [...current.bonusGroups, { keywords: '', mode: 'TRIGGER' }] }))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (productId === null) return
    setError(null)
    setBusy(true)
    try {
      await api.registerUsedSearch(productId, buildUsedSearchCommand(form))
      setForm(EMPTY)
      setSearches(await api.listUsedSearches(productId))
      onRegistered?.(productId)
    } catch (failure) {
      setError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="중고 검색 등록">
      <h2>중고 검색 등록</h2>
      <p role="note">
        번개장터에서 매물을 3계층으로 거릅니다 — <strong>필수 키워드</strong>는 전부 있어야 하고(AND),
        <strong> 제외 키워드</strong>는 하나라도 있으면 즉시 탈락(NOT)합니다. <strong>보너스 그룹</strong>은
        TRIGGER면 그룹 중 하나 이상 있어야 알림 조건을 채우고, SORT면 배지·정렬에만 씁니다.
      </p>

      <label>
        제품
        <select
          value={productId ?? ''}
          onChange={(event) => setProductId(event.target.value === '' ? null : Number(event.target.value))}
        >
          <option value="">선택하세요</option>
          {products.map((product) => (
            <option key={product.productId} value={product.productId}>
              {product.name}
            </option>
          ))}
        </select>
      </label>

      {productId !== null && (
        <form onSubmit={submit} aria-label="중고 검색 등록 폼">
          <label>
            필수 키워드 (쉼표 또는 줄바꿈)
            <textarea
              value={form.required}
              onChange={(event) => setForm((c) => ({ ...c, required: event.target.value }))}
              placeholder="아이폰17, 256"
            />
          </label>
          <label>
            제외 키워드
            <textarea
              value={form.exclude}
              onChange={(event) => setForm((c) => ({ ...c, exclude: event.target.value }))}
              placeholder="파손, 침수, 부품용"
            />
          </label>

          <fieldset>
            <legend>보너스 그룹</legend>
            {form.bonusGroups.map((group, index) => (
              <div key={index}>
                <label>
                  키워드
                  <input
                    value={group.keywords}
                    onChange={(event) => setBonusGroup(index, { keywords: event.target.value })}
                    placeholder="미개봉, 새제품"
                  />
                </label>
                <label>
                  모드
                  <select
                    value={group.mode}
                    onChange={(event) => setBonusGroup(index, { mode: event.target.value as BonusMode })}
                  >
                    <option value="TRIGGER">TRIGGER — 알림 조건</option>
                    <option value="SORT">SORT — 배지·정렬만</option>
                  </select>
                </label>
              </div>
            ))}
            <button type="button" onClick={addBonusGroup}>
              보너스 그룹 추가
            </button>
          </fieldset>

          <label>
            목표가 (원, 선택)
            <input
              value={form.targetPrice}
              onChange={(event) => setForm((c) => ({ ...c, targetPrice: event.target.value }))}
              placeholder="비우면 가격 조건 없이 필터 통과만으로 알림"
            />
          </label>
          <label>
            폴링 주기 (분, 선택 — 하한 10분)
            <input
              value={form.pollIntervalMin}
              onChange={(event) => setForm((c) => ({ ...c, pollIntervalMin: event.target.value }))}
              placeholder="10"
            />
          </label>

          <button type="submit" disabled={busy}>
            {busy ? '등록 중...' : '등록'}
          </button>
        </form>
      )}

      {error && <p role="alert">{error}</p>}

      {productId !== null && (
        <>
          <h3>등록된 중고 검색</h3>
          {searches === null ? (
            <p className="loading">불러오는 중...</p>
          ) : searches.length === 0 ? (
            <p className="empty">등록된 중고 검색이 없습니다.</p>
          ) : (
            <ul aria-label="등록된 중고 검색">
              {searches.map((search) => (
                <li key={search.usedSearchId}>
                  <code>#{search.usedSearchId}</code> {search.required.join(' + ')}
                  {search.targetPrice !== null && <> · 목표가 {search.targetPrice.toLocaleString('en-US')}원</>}
                  {' · '}
                  {search.pollIntervalMin}분 주기
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  )
}
