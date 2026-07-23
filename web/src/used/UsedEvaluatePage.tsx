import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { EvaluationKind, EvaluationResponse, UsedSearchView } from '../api/types'

const KIND_LABEL: Record<EvaluationKind, string> = {
  URL: 'URL',
  TEXT: '텍스트 붙여넣기',
  MANUAL: '직접 입력',
}

function describeError(failure: unknown): string {
  if (failure instanceof ApiFailure) return `평가 실패 (${failure.code})`
  return '평가 실패: 알 수 없는 오류'
}

/**
 * USED-04 평가기(AC-12·13·14). 입력 3단(URL→TEXT→MANUAL) 폴백 — URL은 실 fetch하지 않고
 * 이미 폴링해 아는 매물인지만 core가 본다(docs/91 Q-76). 못 찾으면 다음 단계를 요청한다.
 */
export function UsedEvaluatePage() {
  const [searches, setSearches] = useState<UsedSearchView[]>([])
  const [usedSearchId, setUsedSearchId] = useState<number | null>(null)
  const [kind, setKind] = useState<EvaluationKind>('MANUAL')
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')
  const [title, setTitle] = useState('')
  const [price, setPrice] = useState('')
  const [result, setResult] = useState<EvaluationResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    // 등록된 검색이 있는 제품을 찾을 방법이 없어 전체 제품을 훑는다 — 1인용 규모라 과하지 않다.
    api
      .listProducts()
      .then((products) => Promise.all(products.map((p) => api.listUsedSearches(p.productId))))
      .then((lists) => setSearches(lists.flat()))
      .catch(() => setError('등록된 중고 검색을 불러오지 못했습니다.'))
  }, [])

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    if (usedSearchId === null) return
    setError(null)
    setBusy(true)
    setResult(null)
    try {
      const response = await api.evaluateListing(usedSearchId, {
        kind,
        url: kind === 'URL' ? url : null,
        text: kind === 'TEXT' ? text : null,
        title: kind === 'MANUAL' ? title : null,
        price: kind === 'MANUAL' && price !== '' ? Number(price) : null,
        variantId: null,
      })
      setResult(response)
      // core가 다음 단계를 요청하면 그 단계로 자동 전환한다 — 사람이 같은 정보를 두 번 안 고르게.
      if (response.needsInput) setKind(response.needsInput)
    } catch (failure) {
      setError(describeError(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="중고 매물 평가">
      <h2>중고 매물 평가</h2>
      <p role="note">
        URL은 실제로 접속하지 않습니다 — 이미 폴링해 아는 매물인지만 확인합니다. 모르는 매물이면
        텍스트 붙여넣기를, 그것도 안 되면 직접 입력을 요청합니다.
      </p>

      <label>
        중고 검색
        <select
          value={usedSearchId ?? ''}
          onChange={(event) => setUsedSearchId(event.target.value === '' ? null : Number(event.target.value))}
        >
          <option value="">선택하세요</option>
          {searches.map((search) => (
            <option key={search.usedSearchId} value={search.usedSearchId}>
              #{search.usedSearchId} {search.required.join(' + ')}
            </option>
          ))}
        </select>
      </label>

      {usedSearchId !== null && (
        <form onSubmit={submit} aria-label="평가 입력">
          <label>
            입력 방식
            <select value={kind} onChange={(event) => setKind(event.target.value as EvaluationKind)}>
              {(Object.keys(KIND_LABEL) as EvaluationKind[]).map((k) => (
                <option key={k} value={k}>
                  {KIND_LABEL[k]}
                </option>
              ))}
            </select>
          </label>

          {kind === 'URL' && (
            <label>
              매물 URL
              <input value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://m.bunjang.co.kr/products/..." />
            </label>
          )}
          {kind === 'TEXT' && (
            <label>
              붙여넣은 본문
              <textarea value={text} onChange={(event) => setText(event.target.value)} placeholder="매물 설명 전문을 붙여넣으세요" />
            </label>
          )}
          {kind === 'MANUAL' && (
            <>
              <label>
                제목
                <input value={title} onChange={(event) => setTitle(event.target.value)} />
              </label>
              <label>
                가격 (원)
                <input value={price} onChange={(event) => setPrice(event.target.value)} />
              </label>
            </>
          )}

          <button type="submit" disabled={busy}>
            {busy ? '평가 중...' : '평가'}
          </button>
        </form>
      )}

      {error && <p role="alert">{error}</p>}

      {result?.needsInput && (
        <p role="status" aria-label="입력 요청">
          이 입력으로는 매물을 읽지 못했습니다 — <strong>{KIND_LABEL[result.needsInput]}</strong>으로 다시
          시도하세요.
        </p>
      )}

      {result?.listing && (
        <section aria-label="평가 결과">
          <h3>{result.listing.title}</h3>
          <p>{result.listing.price.toLocaleString('en-US')}원</p>
          {result.listing.url && (
            <a href={result.listing.url} target="_blank" rel="noreferrer">
              원문
            </a>
          )}

          {result.priceContext && (
            <div aria-label="가격 맥락">
              {result.priceContext.benchmarkComparisonPercent !== null ? (
                <p>신품 기준가 대비 {result.priceContext.benchmarkComparisonPercent}%</p>
              ) : (
                <p>신품 기준가 비교 안 함 — variant를 지정하지 않았습니다.</p>
              )}
              <p>
                {/* core의 source가 이미 "번개장터 활성 매물"이라 여기서 "활성 매물"을 또 붙이면
                    "활성 매물 활성 매물 0건"처럼 중복된다 — 실제 화면에서 발견했다. */}
                {result.priceContext.source} {result.priceContext.activeSnapshotPrices.length}건:{' '}
                {result.priceContext.activeSnapshotPrices.map((p) => p.toLocaleString('en-US')).join(', ') || '없음'}
              </p>
            </div>
          )}

          {result.riskSignals && result.riskSignals.length > 0 && (
            <ul aria-label="위험 신호">
              {result.riskSignals.map((signal, i) => (
                <li key={i}>
                  {signal.category}: {signal.detail}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </section>
  )
}
