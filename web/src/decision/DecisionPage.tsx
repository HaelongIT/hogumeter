import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { BenchmarkView, CadenceView, CoupangLatestPrice, ProductSummary, SignalView } from '../api/types'
import { AlertPolicyPanel } from '../policy/AlertPolicyPanel'
import { PurchasePanel } from '../purchase/PurchasePanel'
import { Gauge } from './Gauge'
import {
  benchmarkLine,
  cadenceLine,
  conditionsSuffix,
  coupangPriceLine,
  gapLine,
  lowestLine,
  signalBadge,
  verdictSubline,
} from './present'

interface Loaded {
  signal: SignalView
  benchmark: BenchmarkView
  cadence: CadenceView
  coupang: CoupangLatestPrice
}

const describe = (failure: unknown) =>
  failure instanceof ApiFailure ? `조회 실패 (${failure.code})` : '조회 실패 — core가 떠 있는지 확인하세요.'

/**
 * 제품 목록을 (제품, variant) 쌍으로 편다. variant가 없는 제품은 고를 수 없다.
 *
 * 분리(SPLIT) 제품이면 수요축을 함께 들고 온다 — 그 값을 지정해야 조회가 된다(Q-66 ①).
 */
function selectable(products: ProductSummary[]) {
  return products.flatMap((product) =>
    product.variants.map((variant) => ({
      variantId: variant.variantId,
      label: `${product.name} — ${variant.label}`,
      demandAxis: product.demandAxisMode === 'SPLIT' ? (product.axes.find((a) => a.axisType === 'DEMAND') ?? null) : null,
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
  const [options, setOptions] = useState<ReturnType<typeof selectable>>([])
  const [variantId, setVariantId] = useState<number | null>(initialVariantId)
  const [periodMonths, setPeriodMonths] = useState<number>(6)
  const [demandAxisValue, setDemandAxisValue] = useState<string | null>(null)
  // Q-11: 기본 숨김 + 사용자 토글. 켜도 기준가 계산은 안 바뀐다 — 표시 전용 목록만 채워진다.
  const [includeOutliers, setIncludeOutliers] = useState(false)
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [error, setError] = useState<string | null>(null)

  // 분리 제품이면 어느 축 값을 볼지 골라야 한다 — 안 고르면 core가 400을 낸다(전체로 답하면 묶음의 거짓말).
  const demandAxis = options.find((option) => option.variantId === variantId)?.demandAxis ?? null

  useEffect(() => {
    api
      .listProducts()
      .then((products) => setOptions(selectable(products)))
      .catch(() => setError('제품 목록을 불러오지 못했습니다.'))
  }, [])

  // variant를 바꾸면 이전 제품의 색이 남아 있으면 안 된다 — 남으면 조용히 엉뚱한 값으로 조회한다.
  useEffect(() => {
    setDemandAxisValue(null)
  }, [variantId])

  useEffect(() => {
    if (variantId === null) return
    if (demandAxis !== null && demandAxisValue === null) return // 색을 고르기 전엔 묻지 않는다
    let live = true
    setError(null)
    setLoaded(null)

    // 넷은 서로 독립이다. 하나가 실패하면 화면을 반쪽만 그리지 않고 실패를 말한다.
    // 신호등·기준가는 **같은 수요축 값**으로 부른다 — 다르면 한 화면이 서로 다른 사실을 말한다.
    Promise.all([
      api.getSignal(variantId, demandAxisValue), // 기간 무관 — core가 6개월로 고정한다
      api.getBenchmark(variantId, periodMonths, demandAxisValue, includeOutliers),
      api.getCadence(variantId, periodMonths),
      api.getCoupangLatestPrice(variantId), // CMP-01 재료 — 확장 미연동이면 전 필드 null(실패 아님)
    ])
      .then(
        ([signal, benchmark, cadence, coupang]) => live && setLoaded({ signal, benchmark, cadence, coupang }),
      )
      .catch((failure) => live && setError(describe(failure)))

    return () => {
      live = false
    }
  }, [variantId, periodMonths, demandAxis, demandAxisValue, includeOutliers])

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

      {/* Q-11: 기본 숨김. 켜도 기준가·신호는 안 바뀐다 — 계산에서 제외된 값을 참고로만 보여준다.
          별도 행으로 둔다 — 위 context-row는 2열 그리드라 세 번째 항목은 어그러진다. */}
      <label className="outlier-toggle">
        <input
          type="checkbox"
          checked={includeOutliers}
          onChange={(event) => setIncludeOutliers(event.target.checked)}
        />
        이상치 포함(참고용)
      </label>

      {/* 분리(SPLIT) 제품은 값마다 분포가 다르다 — 어느 값을 볼지 사람이 골라야 답할 수 있다(Q-66 ①). */}
      {demandAxis !== null && (
        <div className="context-row">
          <label>
            {demandAxis.name}
            <select
              value={demandAxisValue ?? ''}
              onChange={(event) => setDemandAxisValue(event.target.value === '' ? null : event.target.value)}
            >
              <option value="">선택하세요</option>
              {demandAxis.allowedValues.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </label>
        </div>
      )}
      {demandAxis !== null && demandAxisValue === null && (
        <p role="note" aria-label="수요축 안내">
          이 제품은 <strong>{demandAxis.name}</strong>별로 기준가를 따로 냅니다(분리). 어느 {demandAxis.name}을(를)
          볼지 고르세요 — 전부 합쳐서 하나로 내면 그건 묶음이지 분리가 아닙니다.
        </p>
      )}

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
            {/* CMP-01 — 쿠팡 크롬 확장이 보낸 관측(확장 미연동이면 미확인). 지어내지 않는다(원칙 6). */}
            <p aria-label="쿠팡 관측가" className="readout">
              {coupangPriceLine(loaded.coupang)}
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

          {/* Q-11 — 계산 진실과 무관한 참고 목록이다. 이 딜들은 기준가·신호 어디에도 반영되지
              않았다 — 판단은 사람이 원문을 보고 한다(절대 원칙 2). */}
          {includeOutliers && loaded.benchmark.outliers.length > 0 && (
            <section aria-label="이상치" className="outliers">
              <h2>이상치 {loaded.benchmark.outliers.length}건(기준가 계산에서 제외됨)</h2>
              <ul>
                {loaded.benchmark.outliers.map((deal) => (
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
      {variantId !== null && <PurchasePanel variantId={variantId} demandAxisValue={demandAxisValue} />}

      {/* "지금은 아니다"의 다음 행동은 "그럼 얼마면 알려줘"다. 그래서 판단 화면에 둔다(REG-03). */}
      {variantId !== null && <AlertPolicyPanel variantId={variantId} />}
    </main>
  )
}
