import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { AlertPolicyView } from '../api/types'
import { InvalidForm } from '../registration/buildCommand'
import { buildPolicyCommand, type PolicyForm } from './buildPolicyCommand'

const EMPTY: PolicyForm = {
  targetPrice: '',
  periodMonths: '',
  quietHoursStart: '',
  quietHoursEnd: '',
  kDisplay: '',
  excludeKeywords: '',
}

/** K 후보(확정본: 3~10). 낮추면 적은 표본으로도 기준가를 말하고, 높이면 더 모일 때까지 사례만 낸다. */
const K_CHOICES = [3, 4, 5, 6, 7, 8, 9, 10] as const

/** 기간 P 후보. 판단 화면의 "표시 기간"과 **다른 손잡이**다 — 이건 알림 판정에 쓰인다. */
const PERIODS = [3, 6, 12] as const

const describe = (failure: unknown) => {
  if (failure instanceof InvalidForm) return failure.message
  if (failure instanceof ApiFailure) return `정책 저장 실패 (${failure.code})`
  return '정책 저장 실패: 알 수 없는 오류'
}

const won = (value: number) => value.toLocaleString('en-US')

/** 저장된 정책 → 폼. 없는 값은 빈 칸이다. `0`으로 채우면 "공짜여야 알림"이 된다. */
function toForm(policy: AlertPolicyView): PolicyForm {
  return {
    targetPrice: policy.targetPrice === undefined ? '' : won(policy.targetPrice),
    periodMonths: policy.periodMonths === undefined ? '' : String(policy.periodMonths),
    quietHoursStart: policy.quietHoursStart === undefined ? '' : String(policy.quietHoursStart),
    quietHoursEnd: policy.quietHoursEnd === undefined ? '' : String(policy.quietHoursEnd),
    // K는 미설정이라도 core가 기본값을 숫자로 준다(정본이 core 상수 하나) — 빈 칸이 될 일이 없다.
    kDisplay: policy.kDisplay === undefined ? '' : String(policy.kDisplay),
    // 제외 키워드는 항상 배열로 온다(없으면 []) — 쉼표로 이어 한 줄로 보여준다.
    excludeKeywords: policy.excludeKeywords.join(', '),
  }
}

/**
 * REG-03 알림 정책 설정. 확정본 §7의 web 최소 슬라이스가 요구하는 "목표가 설정"이 여기다.
 *
 * <p>다루는 것은 여섯 — 목표가·기간 P·방해금지 2개 + <b>K_display</b>(Q-48 ①) + <b>제외 키워드</b>(Q-28).
 * 제외 키워드에 걸리는 딜(리퍼·벌크 등)은 기준가·신호·알림 전 통계에서 빠진다 — 신품 기준가에 중고가
 * 섞이지 않게. ⚠️라벨 모드 토글·수요축 필터는 아직 소비 기능과 함께 매핑을 기다린다(Q-66).
 */
export function AlertPolicyPanel({ variantId }: { variantId: number }) {
  const [form, setForm] = useState<PolicyForm>(EMPTY)
  const [configured, setConfigured] = useState<boolean | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let live = true
    setConfigured(null)
    setError(null)
    setSaved(false)
    api
      .getAlertPolicy(variantId)
      .then((policy) => {
        if (!live) return
        setForm(toForm(policy))
        setConfigured(policy.configured)
      })
      .catch((failure) => {
        if (!live) return
        setError(failure instanceof ApiFailure ? `정책을 불러오지 못했습니다 (${failure.code})` : '정책을 불러오지 못했습니다.')
      })
    return () => {
      live = false
    }
  }, [variantId])

  const set = (key: keyof PolicyForm) => (event: { target: { value: string } }) => {
    setSaved(false)
    setForm((current) => ({ ...current, [key]: event.target.value }))
  }

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const policy = await api.updateAlertPolicy(variantId, buildPolicyCommand(form))
      setForm(toForm(policy))
      setConfigured(policy.configured)
      setSaved(true)
    } catch (failure) {
      setError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  // 못 불러왔으면 폼을 그리지 않는다 — 빈 폼을 저장하면 있던 정책을 덮어쓴다.
  if (configured === null) {
    return <section aria-label="알림 정책">{error ? <p role="alert">{error}</p> : <p>불러오는 중…</p>}</section>
  }

  return (
    <section aria-label="알림 정책">
      <h2>알림 정책</h2>

      {/* 미설정을 "기본값 적용 중"으로 그리면 사용자는 목표가 알림이 켜져 있다고 믿는다. 실제로는
          `alert_policy` 행이 없어 목표가 트리거가 발화하지 않는다(확정본 §107). 판정 기간의 시스템
          기본값은 core의 private 상수라 여기서 숫자로 말하지 않는다(과대약속 금지). */}
      {!configured && (
        <p role="note" aria-label="정책 미설정 안내">
          아직 저장된 정책이 없습니다. <strong>목표가 알림은 발화하지 않습니다.</strong> 판정 기간은 시스템
          기본값을 씁니다.
        </p>
      )}

      <form onSubmit={submit}>
        <label>
          목표가 (원, 비우면 목표가 알림 없음)
          <input inputMode="numeric" value={form.targetPrice} onChange={set('targetPrice')} />
        </label>

        <label>
          알림 판정 기간
          <select value={form.periodMonths} onChange={set('periodMonths')}>
            <option value="">선택하세요</option>
            {PERIODS.map((months) => (
              <option key={months} value={months}>
                최근 {months}개월
              </option>
            ))}
          </select>
        </label>

        {/*
          K_display — 표시를 바꾸는 설정이라 사용자 손잡이다(확정본 §217, 원칙 4). 산식은 시스템이 고정한다.
          이 값이 곧 "몇 건부터 기준가라고 말할 것인가"다 — 낮추면 빨리 말하고 틀릴 위험이 늘고,
          높이면 더 모일 때까지 사례만 낸다. 그 맞바꿈을 문장으로 밝힌다(과대약속 금지).
        */}
        <label>
          기준가 표시 임계 K (몇 건부터 기준가라고 말할지)
          <select value={form.kDisplay} onChange={set('kDisplay')}>
            {K_CHOICES.map((k) => (
              <option key={k} value={k}>
                {k}건 이상
              </option>
            ))}
          </select>
        </label>
        <p role="note" aria-label="K 안내">
          낮추면 표본이 적어도 기준가를 말합니다(빨리 알지만 틀릴 위험이 큽니다). 높이면 더 모일 때까지 기준가
          대신 사례를 냅니다. 이 값은 <strong>표시 기준만</strong> 바꿉니다 — 산식과 수집은 그대로입니다.
        </p>

        {/*
          제외 키워드(Q-28) — 데이터의 진실을 바꾸는 게 아니라 "무엇이 이 제품의 딜인가"를 사용자가 가른다.
          걸리는 딜(리퍼·벌크·해외 등)은 기준가·신호·알림 표본에서 통째로 빠진다 — 신품 기준가가 중고에
          끌려 내려가지 않게. 저장 시점에 굳히지 않고 조회할 때마다 지금 목록에 대고 판정하므로, 나중에
          키워드를 고치면 이미 들어온 딜에도 소급 적용된다.
        */}
        <label>
          제외 키워드 (쉼표로 구분 · 비우면 없음)
          <input
            aria-label="제외 키워드"
            value={form.excludeKeywords}
            onChange={set('excludeKeywords')}
            placeholder="리퍼, 벌크, 해외"
          />
        </label>
        <p role="note" aria-label="제외 키워드 안내">
          제목에 이 단어가 든 딜은 <strong>기준가·신호·알림 표본에서 빠집니다</strong>(리퍼·중고·묶음이 신품
          기준가를 끌어내리지 않게). 목록을 고치면 이미 수집된 딜에도 다시 적용됩니다.
        </p>

        {/* 🔥 대박딜은 방해금지를 관통한다(확정본 §102). 그 사실을 숨기면 "다 막았다"고 믿는다. */}
        <fieldset>
          <legend>방해금지 시간 (시, 0~23 · 끝 시각 제외 · 비우면 없음)</legend>
          <label>
            방해금지 시작
            <input inputMode="numeric" value={form.quietHoursStart} onChange={set('quietHoursStart')} />
          </label>
          <label>
            방해금지 끝
            <input inputMode="numeric" value={form.quietHoursEnd} onChange={set('quietHoursEnd')} />
          </label>
          <p>보류된 알림은 방해금지가 끝나면 발송됩니다. 대박딜(🔥)은 방해금지를 관통합니다.</p>
        </fieldset>

        <button type="submit" disabled={busy}>
          정책 저장
        </button>
      </form>

      {error && <p role="alert">{error}</p>}
      {saved && !error && <p role="status">저장했습니다.</p>}
    </section>
  )
}
