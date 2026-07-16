import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { BenchmarkView, CadenceView, ProductSummary, SignalView } from '../api/types'
import { AlertPolicyPanel } from '../policy/AlertPolicyPanel'
import { PurchasePanel } from '../purchase/PurchasePanel'
import { Gauge } from './Gauge'
import {
  benchmarkLine,
  cadenceLine,
  conditionsSuffix,
  gapLine,
  lowestLine,
  signalBadge,
  verdictSubline,
} from './present'

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

export function DecisionPage({ initialVariantId = null }: { initialVariantId?: number | null } = {}) {
  const [options, setOptions] = useState<{ variantId: number; label: string }[]>([])
  const [variantId, setVariantId] = useState<number | null>(initialVariantId)
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

      <div className="context-row">
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
      </div>

      {options.length === 0 && !error && (
        <div className="empty-decision">
          {/* 꺼진 계기 — 등록하면 여기 계기가 켜진다(빈 화면은 초대다). */}
          <div className="gauge gauge--uncal" aria-hidden="true">
            <div className="gauge-bar" />
          </div>
          <p className="empty">등록된 variant가 없습니다. 먼저 제품을 등록하세요.</p>
        </div>
      )}
      {error && <p role="alert">{error}</p>}

      {variantId !== null && !loaded && !error && (
        <div className="summary-skeleton" aria-hidden="true">
          <div className="skeleton skeleton-verdict" />
          <div className="skeleton skeleton-gauge" />
          <div className="skeleton skeleton-readouts" />
        </div>
      )}

      {loaded && badge && (
        <section aria-label="판단 요약" className="summary">
          {/* 판정 히어로 — 이 화면의 답이다. 램프가 신호색을 들고(장식 아님), 헤드라인이 답을 말한다.
              스크린리더에겐 문구가 곧 신호라 램프는 aria-hidden — 색을 따로 읽어 줄 필요가 없다. */}
          <div aria-label="신호등" className="verdict" data-signal={loaded.signal.color}>
            <span className="lamp" aria-hidden="true" />
            <div className="answer">
              <p className="headline">{badge.text}</p>
              <p className="subline">{verdictSubline(loaded.benchmark)}</p>
            </div>
          </div>
          {/* 기간을 바꿔도 신호등은 안 바뀐다. 그 사실을 숨기면 사용자는 바뀐 줄 안다(과대약속 금지). */}
          {periodMonths !== SIGNAL_PERIOD_MONTHS && (
            <p role="note" aria-label="신호등 기간 안내">
              신호등은 기간 설정과 무관하게 최근 {SIGNAL_PERIOD_MONTHS}개월로 판정합니다. 아래 기준가·주기만
              최근 {periodMonths}개월입니다.
            </p>
          )}
          {badge.notes.length > 0 && (
            <ul aria-label="딱지" className="tags">
              {badge.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          )}

          {/* 여기부터 답이 아니라 근거다 — 히어로가 커진 만큼 그 경계를 표식으로 못박는다. */}
          <p className="evidence-label">근거 · 계기</p>

          {/* 계기(the meter) — 실측 위치만 그린다. 표본/현재가 없으면 스스로 "미확립"이라 말한다. */}
          <Gauge view={loaded.benchmark} />

          <div className="readouts">
            <p aria-label="기준가" className="readout">
              {benchmarkLine(loaded.benchmark)}
            </p>
            <p aria-label="갭" className="readout">
              {gapLine(loaded.benchmark)}
            </p>
            {/* "기준가보다 비싸다"만으로는 기다릴지 말지 못 정한다 — 이 기간에 얼마까지 내려갔었나. */}
            {lowestLine(loaded.benchmark) && (
              <p aria-label="기간 최저" className="readout">
                {lowestLine(loaded.benchmark)}
              </p>
            )}
            <p aria-label="딜 주기" className="readout">
              {cadenceLine(loaded.cadence)}
            </p>
            {loaded.benchmark.latestDeal && (
              <p aria-label="최근 딜" className="readout">
                최근 딜 {loaded.benchmark.latestDeal.date} · {loaded.benchmark.latestDeal.site}
                {conditionsSuffix(loaded.benchmark.latestDeal.conditions)} ·{' '}
                <a href={loaded.benchmark.latestDeal.sourceUrl} target="_blank" rel="noreferrer">
                  원문
                </a>
              </p>
            )}
          </div>

          {/* SPARSE면 기준가 대신 사례를 그대로 보여준다 — 판단은 사람이 한다(절대 원칙 2). */}
          {loaded.benchmark.cases.length > 0 && (
            <section aria-label="사례" className="cases">
              <h2>사례 {loaded.benchmark.cases.length}건</h2>
              <ul>
                {loaded.benchmark.cases.map((deal) => (
                  <li key={deal.sourceUrl}>
                    {deal.date} · {deal.price.toLocaleString('en-US')}원 · {deal.site}
                    {conditionsSuffix(deal.conditions)} ·{' '}
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

      {/* 판단 바로 아래에 기록을 둔다 — 사후에 "호구였나"를 물으려면 같은 variant 문맥이어야 한다.
          조회가 실패해도(위의 error) 이미 산 것을 기록하는 길은 막지 않는다. */}
      {variantId !== null && <PurchasePanel variantId={variantId} />}

      {/* "지금은 아니다"의 다음 행동은 "그럼 얼마면 알려줘"다. 그래서 판단 화면에 둔다(REG-03). */}
      {variantId !== null && <AlertPolicyPanel variantId={variantId} />}
    </main>
  )
}
