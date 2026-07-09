import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { BenchmarkView, CadenceView, ProductSummary, SignalView } from '../api/types'
import { benchmarkLine, cadenceLine, gapLine, signalBadge } from './present'

interface Loaded {
  signal: SignalView
  benchmark: BenchmarkView
  cadence: CadenceView
}

const describe = (failure: unknown) =>
  failure instanceof ApiFailure ? `조회 실패 (${failure.code})` : '조회 실패 — core가 떠 있는지 확인하세요.'

/** 제품 목록을 (제품, variant) 쌍으로 편다. variant가 없는 제품은 고를 수 없다. */
function selectable(products: ProductSummary[]) {
  return products.flatMap((product) =>
    product.variants.map((variant) => ({
      variantId: variant.variantId,
      label: `${product.name} — ${variant.label}`,
    })),
  )
}

export function DecisionPage() {
  const [options, setOptions] = useState<{ variantId: number; label: string }[]>([])
  const [variantId, setVariantId] = useState<number | null>(null)
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listProducts()
      .then((products) => setOptions(selectable(products)))
      .catch(() => setError('제품 목록을 불러오지 못했습니다.'))
  }, [])

  useEffect(() => {
    if (variantId === null) return
    let live = true
    setError(null)
    setLoaded(null)

    // 셋은 서로 독립이다. 하나가 실패하면 화면을 반쪽만 그리지 않고 실패를 말한다.
    Promise.all([api.getSignal(variantId), api.getBenchmark(variantId), api.getCadence(variantId)])
      .then(([signal, benchmark, cadence]) => live && setLoaded({ signal, benchmark, cadence }))
      .catch((failure) => live && setError(describe(failure)))

    return () => {
      live = false
    }
  }, [variantId])

  const badge = loaded && signalBadge(loaded.signal)

  return (
    <main>
      <h1>지금 사도 되나</h1>

      <label>
        variant
        <select
          value={variantId ?? ''}
          onChange={(event) => setVariantId(event.target.value === '' ? null : Number(event.target.value))}
        >
          <option value="">선택하세요</option>
          {options.map((option) => (
            <option key={option.variantId} value={option.variantId}>
              {option.label}
            </option>
          ))}
        </select>
      </label>

      {options.length === 0 && !error && <p>등록된 variant가 없습니다. 먼저 제품을 등록하세요.</p>}
      {error && <p role="alert">{error}</p>}

      {loaded && badge && (
        <section aria-label="판단 요약">
          <p aria-label="신호등">
            <span aria-label={`신호 ${loaded.signal.color}`}>{badge.mark}</span> {badge.text}
          </p>
          {badge.notes.length > 0 && (
            <ul aria-label="딱지">
              {badge.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          )}

          <p aria-label="기준가">{benchmarkLine(loaded.benchmark)}</p>
          <p aria-label="갭">{gapLine(loaded.benchmark)}</p>
          <p aria-label="딜 주기">{cadenceLine(loaded.cadence)}</p>

          {loaded.benchmark.latestDeal && (
            <p aria-label="최근 딜">
              최근 딜 {loaded.benchmark.latestDeal.date} · {loaded.benchmark.latestDeal.site} ·{' '}
              <a href={loaded.benchmark.latestDeal.sourceUrl} target="_blank" rel="noreferrer">
                원문
              </a>
            </p>
          )}

          {/* SPARSE면 기준가 대신 사례를 그대로 보여준다 — 판단은 사람이 한다(절대 원칙 2). */}
          {loaded.benchmark.cases.length > 0 && (
            <section aria-label="사례">
              <h2>사례 {loaded.benchmark.cases.length}건</h2>
              <ul>
                {loaded.benchmark.cases.map((deal) => (
                  <li key={deal.sourceUrl}>
                    {deal.date} · {deal.price.toLocaleString('en-US')}원 · {deal.site} ·{' '}
                    <a href={deal.sourceUrl} target="_blank" rel="noreferrer">
                      원문
                    </a>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </section>
      )}
    </main>
  )
}
