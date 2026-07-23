import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { ComparisonView, ProductSummary } from '../api/types'

function describe(failure: unknown, fallback: string): string {
  if (failure instanceof ApiFailure) return `${fallback} (${failure.code})`
  return `${fallback}: 알 수 없는 오류`
}

/** 한 매물 행 — 축값 입력·메모 추가를 인라인으로 한다. */
function ListingRow({
  row,
  axes,
  onChanged,
}: {
  row: ComparisonView['rows'][number]
  axes: ComparisonView['axes']
  onChanged: () => void
}) {
  const [noteBody, setNoteBody] = useState('')
  const [axisEdits, setAxisEdits] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function addNote() {
    if (!noteBody.trim()) return
    setBusy(true)
    setError(null)
    try {
      await api.addListingNote(row.listingId, noteBody.trim())
      setNoteBody('')
      onChanged()
    } catch (failure) {
      setError(describe(failure, '메모 저장 실패'))
    } finally {
      setBusy(false)
    }
  }

  async function promote(axisId: number) {
    const value = axisEdits[axisId]?.trim()
    if (!value) return
    setBusy(true)
    setError(null)
    try {
      await api.promoteAxisValue(row.listingId, { axisId, value })
      setAxisEdits((current) => ({ ...current, [axisId]: '' }))
      onChanged()
    } catch (failure) {
      setError(describe(failure, '값 승격 실패'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <tr>
      <td>
        {row.url ? (
          <a href={row.url} target="_blank" rel="noreferrer">
            {row.title}
          </a>
        ) : (
          row.title
        )}
        <br />
        {row.price.toLocaleString('en-US')}원
      </td>
      {axes.map((axis) => {
        const key = String(axis.id)
        const value = row.axisValues[key]
        return (
          <td key={axis.id}>
            {/* 빈칸은 "확인 안 한 항목" 체크리스트다 — null을 감추지 않는다(AC-18). */}
            {value !== undefined ? value : <span aria-label={`${axis.name} 미확인`}>—</span>}
            <div>
              <input
                aria-label={`${row.title} ${axis.name} 값 입력`}
                value={axisEdits[axis.id] ?? ''}
                onChange={(event) => setAxisEdits((c) => ({ ...c, [axis.id]: event.target.value }))}
                placeholder={value ?? '값 입력'}
              />
              <button type="button" disabled={busy} onClick={() => promote(axis.id)}>
                승격
              </button>
            </div>
          </td>
        )
      })}
      <td>
        <ul aria-label={`${row.title} 메모`}>
          {row.notes.map((note, i) => (
            <li key={i}>{note}</li>
          ))}
        </ul>
        <input
          aria-label={`${row.title} 메모 입력`}
          value={noteBody}
          onChange={(event) => setNoteBody(event.target.value)}
          placeholder="자유 메모"
        />
        <button type="button" disabled={busy} onClick={addNote}>
          메모 추가
        </button>
        {error && <p role="alert">{error}</p>}
      </td>
    </tr>
  )
}

/**
 * USED-05 병렬 비교표(AC-16·17·18). 축은 <b>추가 전용</b>이다 — core가 이미 정의된 축을 지우지
 * 않는다(PUT은 전체 교체가 아니다). 소실된 매물은 core가 이미 빼고 준다.
 */
export function UsedComparisonPage() {
  const [products, setProducts] = useState<ProductSummary[]>([])
  const [productId, setProductId] = useState<number | null>(null)
  const [newAxisName, setNewAxisName] = useState('')
  const [comparison, setComparison] = useState<ComparisonView | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api
      .listProducts()
      .then(setProducts)
      .catch(() => setError('제품 목록을 불러오지 못했습니다.'))
  }, [])

  const reload = () => {
    if (productId === null) return
    api
      .getComparison(productId)
      .then(setComparison)
      .catch(() => setError('비교표를 불러오지 못했습니다.'))
  }

  useEffect(() => {
    setComparison(null)
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [productId])

  async function addAxis(event: React.FormEvent) {
    event.preventDefault()
    if (productId === null || !newAxisName.trim()) return
    setBusy(true)
    setError(null)
    try {
      await api.defineComparisonAxes(productId, [newAxisName.trim()])
      setNewAxisName('')
      reload()
    } catch (failure) {
      setError(describe(failure, '축 정의 실패'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="중고 비교">
      <h2>중고 비교</h2>

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
        <>
          <form onSubmit={addAxis} aria-label="비교축 추가">
            <label>
              비교축 이름
              <input value={newAxisName} onChange={(event) => setNewAxisName(event.target.value)} placeholder="배터리%" />
            </label>
            <button type="submit" disabled={busy}>
              축 추가
            </button>
          </form>
          <p role="note">
            축은 <strong>추가만</strong> 됩니다 — 기존 축은 지워지지 않습니다.
          </p>

          {error && <p role="alert">{error}</p>}

          {comparison === null ? (
            <p className="loading">불러오는 중...</p>
          ) : comparison.rows.length === 0 ? (
            <p className="empty">
              비교할 매물이 없습니다. 중고 검색이 실제로 폴링돼야 매물이 쌓입니다.
            </p>
          ) : (
            <table aria-label="병렬 비교표">
              <thead>
                <tr>
                  <th>매물</th>
                  {comparison.axes.map((axis) => (
                    <th key={axis.id}>{axis.name}</th>
                  ))}
                  <th>메모</th>
                </tr>
              </thead>
              <tbody>
                {comparison.rows.map((row) => (
                  <ListingRow key={row.listingId} row={row} axes={comparison.axes} onChanged={reload} />
                ))}
              </tbody>
            </table>
          )}
        </>
      )}
    </section>
  )
}
