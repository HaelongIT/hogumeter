import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'

/** 쉼표 한 줄 ↔ 배열. 정규화(공백·중복)의 정본은 core다 — 여기선 보내기 전 최소 정리만 한다. */
const toList = (line: string) =>
  line
    .split(',')
    .map((k) => k.trim())
    .filter((k) => k.length > 0)

const describe = (failure: unknown) => {
  if (failure instanceof ApiFailure) return `저장 실패 (${failure.code})`
  return '저장 실패: 알 수 없는 오류'
}

/**
 * 전역 설정(Q-28 ①) — 지금은 **전역 제외 키워드** 하나다.
 *
 * <p>"리퍼"·"중고"처럼 **어느 제품에나 같은 뜻인** 노이즈를 여기 한 번 적는다. 제품마다 옮겨 적다
 * 한 곳을 빠뜨리면 그 제품만 조용히 오염되기 때문이다. 제품에만 있는 노이즈는 판단 화면의
 * 알림 정책(제품별 제외 키워드)에 적는다 — 둘은 **합집합**으로 적용된다.
 */
export function SettingsPage() {
  const [line, setLine] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let live = true
    api
      .getGlobalExcludeKeywords()
      .then((view) => {
        if (!live) return
        setLine(view.excludeKeywords.join(', '))
        setLoaded(true)
      })
      .catch(() => live && setError('전역 설정을 불러오지 못했습니다.'))
    return () => {
      live = false
    }
  }, [])

  const submit = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    setError(null)
    setSaved(null)
    setBusy(true)
    try {
      // core가 정규화한 결과를 그대로 돌려받아 화면에 반영한다 — 무엇이 실제로 저장됐는지 보여준다.
      const view = await api.updateGlobalExcludeKeywords(toList(line))
      setLine(view.excludeKeywords.join(', '))
      setSaved(view.excludeKeywords.length === 0 ? '저장했습니다 — 전역 제외 없음' : '저장했습니다')
    } catch (failure) {
      setError(describe(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <section aria-label="전역 설정">
      <h2>전역 설정</h2>

      <form onSubmit={submit} aria-label="전역 제외 키워드">
        <label>
          전역 제외 키워드
          <input
            aria-label="전역 제외 키워드 입력"
            value={line}
            onChange={(event) => setLine(event.target.value)}
            placeholder="리퍼, 중고, 파손"
            disabled={!loaded}
          />
        </label>
        <p role="note" aria-label="전역 제외 키워드 안내">
          제목에 이 단어가 든 딜은 <strong>모든 제품</strong>의 기준가·신호·알림 표본에서 빠집니다. 제품별
          제외 키워드(판단 화면의 알림 정책)와 <strong>합쳐서</strong> 적용되고, 목록을 고치면 이미 수집된
          딜에도 다시 적용됩니다.
        </p>
        <button type="submit" disabled={busy || !loaded}>
          저장
        </button>
      </form>

      {error && <p role="alert">{error}</p>}
      {saved && <p role="status">{saved}</p>}
    </section>
  )
}
