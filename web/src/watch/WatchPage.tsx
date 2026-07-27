import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { WatchItemView } from '../api/types'
import { dateLine, priceLine, stateLabel } from './present'

type SubTab = 'active' | 'resolved'

const describeLoad = (failure: unknown) =>
  failure instanceof ApiFailure
    ? `목록을 불러오지 못했습니다 (${failure.code})`
    : '목록을 불러오지 못했습니다 — core가 떠 있는지 확인하세요.'

const describeAction = (failure: unknown) =>
  failure instanceof ApiFailure ? `처리하지 못했습니다 (${failure.code})` : '처리하지 못했습니다 — 알 수 없는 오류'

/** 딜을 아직 안 지켜본 사람이 봐도 뜻을 알 수 있게 — 메모가 없으면 딜 ID로 대신 가리킨다. */
function subject(item: WatchItemView): string {
  return item.note ?? `딜 #${item.dealEventId}`
}

/**
 * WATCH(docs/17) 딜 보관함 — 활성 탭(관찰 중인 핀)·회고 탭(결말난 핀). 판단 화면(사례·최근 딜)에
 * 📌 핀 버튼이 따로 있고(`DecisionPage`), 여기서도 딜 ID를 알면 직접 핀할 수 있다.
 */
export function WatchPage() {
  const [subTab, setSubTab] = useState<SubTab>('active')
  const [active, setActive] = useState<WatchItemView[] | null>(null)
  const [resolved, setResolved] = useState<WatchItemView[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)
  const [dealIdInput, setDealIdInput] = useState('')
  const [noteInput, setNoteInput] = useState('')

  const loadActive = () =>
    api
      .listActiveWatchItems()
      .then(setActive)
      .catch((failure) => setError(describeLoad(failure)))

  useEffect(() => {
    let live = true
    api
      .listActiveWatchItems()
      .then((loaded) => live && setActive(loaded))
      .catch((failure) => live && setError(describeLoad(failure)))
    return () => {
      live = false
    }
  }, [])

  useEffect(() => {
    if (subTab !== 'resolved' || resolved !== null) return
    let live = true
    api
      .listResolvedWatchItems()
      .then((loaded) => live && setResolved(loaded))
      .catch((failure) => live && setError(describeLoad(failure)))
    return () => {
      live = false
    }
  }, [subTab, resolved])

  async function pin() {
    const dealEventId = Number(dealIdInput)
    if (!Number.isInteger(dealEventId) || dealIdInput.trim() === '') {
      setActionError('딜 ID는 숫자여야 합니다.')
      return
    }
    setActionError(null)
    try {
      await api.pinDeal({ dealEventId, note: noteInput.trim() === '' ? null : noteInput })
      setDealIdInput('')
      setNoteInput('')
      await loadActive()
    } catch (failure) {
      setActionError(describeAction(failure))
    }
  }

  async function resolve(watchItemId: number, action: 'bought' | 'drop') {
    setBusy(watchItemId)
    setActionError(null)
    try {
      await (action === 'bought' ? api.markWatchItemBought(watchItemId) : api.dropWatchItem(watchItemId))
      await loadActive()
    } catch (failure) {
      setActionError(describeAction(failure))
    } finally {
      setBusy(null)
    }
  }

  return (
    <main>
      <h1>딜 보관함</h1>

      <nav aria-label="보관함 탭">
        <button type="button" aria-current={subTab === 'active'} onClick={() => setSubTab('active')}>
          활성
        </button>
        <button type="button" aria-current={subTab === 'resolved'} onClick={() => setSubTab('resolved')}>
          회고
        </button>
      </nav>

      {error && <p role="alert">{error}</p>}
      {actionError && <p role="alert">{actionError}</p>}

      {subTab === 'active' && (
        <section aria-label="활성 핀">
          <p role="note">
            지켜보고 싶은 딜을 핀으로 꽂아둡니다. 판단 화면의 사례·최근 딜에 📌 핀 버튼이 있고, 딜 ID를
            알면 여기서 직접 걸 수도 있습니다.
          </p>
          <form
            aria-label="핀 추가"
            onSubmit={(event) => {
              event.preventDefault()
              pin()
            }}
          >
            <label>
              딜 ID
              <input
                aria-label="딜 ID"
                inputMode="numeric"
                value={dealIdInput}
                onChange={(event) => setDealIdInput(event.target.value)}
              />
            </label>
            <label>
              메모
              <input aria-label="메모" value={noteInput} onChange={(event) => setNoteInput(event.target.value)} />
            </label>
            <button type="submit">핀하기</button>
          </form>

          {active === null && !error && <p className="loading">불러오는 중…</p>}
          {active !== null && active.length === 0 && <p className="empty">핀한 딜이 없습니다.</p>}
          {active !== null && active.length > 0 && (
            <ul aria-label="활성 핀 목록">
              {active.map((item) => (
                <li key={item.watchItemId}>
                  <span>{subject(item)}</span>
                  <span>{priceLine(item)}</span>
                  <span>{dateLine(item)}</span>
                  <button type="button" disabled={busy !== null} onClick={() => resolve(item.watchItemId, 'bought')}>
                    샀어요
                  </button>
                  <button type="button" disabled={busy !== null} onClick={() => resolve(item.watchItemId, 'drop')}>
                    기각·해제
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {subTab === 'resolved' && (
        <section aria-label="회고">
          {resolved === null && !error && <p className="loading">불러오는 중…</p>}
          {resolved !== null && resolved.length === 0 && <p className="empty">결말난 핀이 없습니다.</p>}
          {resolved !== null && resolved.length > 0 && (
            <ul aria-label="회고 목록">
              {resolved.map((item) => (
                <li key={item.watchItemId}>
                  <span>{subject(item)}</span>
                  <span>{stateLabel(item.state)}</span>
                  <span>{dateLine(item)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </main>
  )
}
