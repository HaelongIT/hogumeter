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

/**
 * 기간 P는 **사용자 손잡이**다(원칙 4 — 표시를 바꾸는 설정은 사용자에게).
 * 확정본 §"기준가 산식(사용자가 기간 P 설정 시 출력)". 산식 자체는 시스템이 고정한다.
 */
const PERIODS = [3, 6, 12] as const

/** core의 `GetSignalUseCase.PERIOD_MONTHS` — 신호등은 이 값으로 고정 판정한다(Q-26 잠정). */
const SIGNAL_PERIOD_MONTHS = 6

export function DecisionPage() {
  const [options, setOptions] = useState<{ variantId: number; label: string }[]>([])
  const [variantId, setVariantId] = useState<number | null>(null)
  const [periodMonths, setPeriodMonths] = useState<number>(6)
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
    Promise.all([
      api.getSignal(variantId), // 기간 무관 — core가 6개월로 고정한다
      api.getBenchmark(variantId, periodMonths),
      api.getCadence(variantId, periodMonths),
    ])
      .then(([signal, benchmark, cadence]) => live && setLoaded({ signal, benchmark, cadence }))
      .catch((failure) => live && setError(describe(failure)))

    return () => {
      live = false
    }
  }, [variantId, periodMonths])

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

      <label>
        기간
        <select value={periodMonths} onChange={(event) => setPeriodMonths(Number(event.target.value))}>
          {PERIODS.map((months) => (
            <option key={months} value={months}>
              최근 {months}개월
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
          {/* 기간을 바꿔도 신호등은 안 바뀐다. 그 사실을 숨기면 사용자는 바뀐 줄 안다(과대약속 금지). */}
          {periodMonths !== SIGNAL_PERIOD_MONTHS && (
            <p role="note">
              신호등은 기간 설정과 무관하게 최근 {SIGNAL_PERIOD_MONTHS}개월로 판정합니다. 아래 기준가·주기만
              최근 {periodMonths}개월입니다.
            </p>
          )}
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
