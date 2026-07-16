import { useEffect, useMemo, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { AxisType, ProductSummary, VariantView } from '../api/types'
import { InvalidForm, buildCommand, type AxisInput, type RegistrationForm } from './buildCommand'

const EMPTY: RegistrationForm = {
  name: '',
  category: '',
  axes: [{ name: '용량', values: '', type: 'PRICE' }],
  aliases: '',
  demandAxisMode: 'GROUPED',
}

/** 등록 직후의 갈 곳. variant가 여럿이면 **고르지 않는다** — 판단은 사람이 한다(절대 원칙 2). */
interface JustRegistered {
  variants: VariantView[]
}

export function RegistrationPage({ onOpenDecision }: { onOpenDecision?: (variantId: number) => void } = {}) {
  const [form, setForm] = useState<RegistrationForm>(EMPTY)
  const [products, setProducts] = useState<ProductSummary[] | null>(null)
  const [registered, setRegistered] = useState<JustRegistered | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const reload = () =>
    api
      .listProducts()
      .then(setProducts)
      .catch(() => setError('제품 목록을 불러오지 못했습니다. core가 떠 있는지 확인하세요.'))

  useEffect(() => {
    void reload()
  }, [])

  // 조합은 눈으로 확인해야 한다 — 축 2개면 variant가 곱셈으로 늘어난다(REG-02).
  const preview = useMemo(() => {
    try {
      return buildCommand(form).variants.map((variant) => variant.label)
    } catch {
      return []
    }
  }, [form])

  const set = (key: 'name' | 'category' | 'aliases' | 'demandAxisMode') => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  const setAxis = (index: number, patch: Partial<AxisInput>) =>
    setForm((current) => ({
      ...current,
      axes: current.axes.map((axis, i) => (i === index ? { ...axis, ...patch } : axis)),
    }))

  const addAxis = () =>
    setForm((current) => ({ ...current, axes: [...current.axes, { name: '', values: '', type: 'PRICE' }] }))

  const removeAxis = (index: number) =>
    setForm((current) => ({ ...current, axes: current.axes.filter((_, i) => i !== index) }))

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const { productId } = await api.registerProduct(buildCommand(form))
      setForm(EMPTY)
      await reload()

      // variant 조회는 **덤**이다. 실패해도 등록이 취소된 건 아니므로 성공을 부정하지 않는다.
      const variants = await api.listVariants(productId).catch(() => [])
      setRegistered({ variants })
    } catch (failure) {
      setError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main>
      <h1>제품 등록</h1>

      <p role="note">
        네이버 후보 검색은 API 키가 없어 아직 쓸 수 없습니다(docs/91 Q-3). 지금은 수동 입력만
        가능합니다 — REG-01이 인정한 폴백 경로입니다.
      </p>

      {registered && (
        <section aria-label="등록 결과">
          <p>등록했습니다. 이제 무엇을 볼지 고르세요.</p>
          {registered.variants.map((variant) => (
            <button key={variant.variantId} type="button" onClick={() => onOpenDecision?.(variant.variantId)}>
              {variant.label} 판단 보기
            </button>
          ))}
        </section>
      )}

      <form onSubmit={submit} aria-label="제품 등록">
        <label>
          제품명
          <input value={form.name} onChange={set('name')} required />
        </label>
        <label>
          카테고리
          <input value={form.category} onChange={set('category')} />
        </label>

        <fieldset>
          <legend>축</legend>
          {/*
            축은 두 종류다(확정본 §38, Q-66 ②). 가격축만 variant를 나눈다 — 예전엔 모든 축을 가격축으로
            보내 색상 같은 축이 variant를 곱했고, 그만큼 표본이 쪼개져 기준가가 이유 없이 빈약해졌다.
            이제 사람이 축마다 고른다. 없는 손잡이를 그리는 게 아니라 실제로 동작한다.
          */}
          <p>
            <strong>가격축</strong>의 조합이 variant가 됩니다(용량 2개 × 모델 2개 = variant 4개).{' '}
            <strong>수요축</strong>(색상 등)은 가격에 영향이 없어 variant를 나누지 않습니다.
          </p>
          {form.axes.map((axis, index) => (
            <div key={index}>
              <label>
                축 {index + 1} 이름
                <input
                  value={axis.name}
                  onChange={(event) => setAxis(index, { name: event.target.value })}
                  placeholder="용량"
                />
              </label>
              <label>
                축 {index + 1} 유형
                <select
                  value={axis.type}
                  onChange={(event) => setAxis(index, { type: event.target.value as AxisType })}
                >
                  <option value="PRICE">가격축 — variant를 나눔</option>
                  <option value="DEMAND">수요축 — 나누지 않음</option>
                </select>
              </label>
              <label>
                축 {index + 1} 값 (쉼표 또는 줄바꿈)
                <textarea
                  value={axis.values}
                  onChange={(event) => setAxis(index, { values: event.target.value })}
                  placeholder="256GB, 512GB"
                />
              </label>
              {form.axes.length > 1 && (
                <button type="button" onClick={() => removeAxis(index)}>
                  축 {index + 1} 삭제
                </button>
              )}
            </div>
          ))}
          <button type="button" onClick={addAxis}>
            축 추가
          </button>
        </fieldset>

        <label>
          별칭 (매칭 사전 시드)
          <textarea value={form.aliases} onChange={set('aliases')} placeholder="아이폰17, iphone17" />
        </label>
        <label>
          수요축 모드
          <select value={form.demandAxisMode} onChange={set('demandAxisMode')}>
            <option value="GROUPED">묶음 (기본)</option>
            <option value="SPLIT">분리</option>
          </select>
        </label>
        {/*
          `SPLIT`은 `product.demand_axis_mode`에 저장되지만 **어떤 프로덕션 코드도 그 값을 보고
          분기하지 않는다**(2026-07-11 실측). 표본 분리도, variant 분리도, `demandAxisValue` 필수
          검증("SPLIT 필수"라는 javadoc)도 없다. 기다리면 된다고 믿게 두는 것이 과대약속이다.
          구현되면 이 문단만 지운다(seam). docs/91 Q-66.
        */}
        <p>분리(SPLIT)는 아직 표본을 나누지 않습니다 — 값은 저장되지만 동작은 묶음과 같습니다.</p>

        {preview.length > 0 && (
          <section aria-label="생성될 variant" className="preview">
            <p className="preview-count">생성될 variant {preview.length}개</p>
            <ul className="preview-list">
              {preview.map((label) => (
                <li key={label}>{label}</li>
              ))}
            </ul>
          </section>
        )}

        <button type="submit" disabled={busy}>
          {busy ? '등록 중...' : '등록'}
        </button>
      </form>

      {error && <p role="alert">{error}</p>}

      <h2>등록된 제품</h2>
      {products === null ? (
        <p className="loading">불러오는 중...</p>
      ) : products.length === 0 ? (
        <p className="empty">아직 등록된 제품이 없습니다. 위에서 첫 제품을 등록하면 여기 쌓입니다.</p>
      ) : (
        <ul aria-label="등록된 제품">
          {products.map((product) => (
            <li key={product.productId} className="product-item">
              <strong>{product.name}</strong>
              <ul>
                {product.variants.map((variant) => (
                  // variantId를 노출한다 — 기준가·신호·주기 조회가 전부 이걸 요구한다.
                  <li key={variant.variantId}>
                    {variant.label} <code>#{variant.variantId}</code>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}

function describe(failure: unknown): string {
  if (failure instanceof InvalidForm) return failure.message
  if (failure instanceof ApiFailure) return `등록 실패 (${failure.code})`
  return '등록 실패: 알 수 없는 오류'
}
