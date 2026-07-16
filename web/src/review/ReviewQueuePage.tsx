import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { ReviewQueueItem } from '../api/types'
import { reviewLine, seenLine } from './present'

const describe = (failure: unknown) =>
  failure instanceof ApiFailure
    ? `미상 큐를 불러오지 못했습니다 (${failure.code})`
    : '미상 큐를 불러오지 못했습니다 — core가 떠 있는지 확인하세요.'

/** 처리 실패는 삼키지 않는다 — 이미 처리된 항목(REVIEW_ITEM_NOT_FOUND)인지 다른 이유인지 code로 드러낸다. */
const describeResolve = (failure: unknown) =>
  failure instanceof ApiFailure
    ? `처리하지 못했습니다 (${failure.code})`
    : '처리하지 못했습니다 — core가 떠 있는지 확인하세요.'

/**
 * 미상 큐. 매칭이 확정하지 못한 딜과 분포 하단 이상치가 여기 쌓인다.
 *
 * <p>이 화면이 생기기 전까지 `review_queue_item`은 쓰이기만 하고 아무도 읽지 않았다 — 매칭이 무엇을
 * 놓치는지 볼 방법이 없었다. 놓침을 허용하는 시스템에서 놓친 것을 볼 수 없다면 그건 유실이다.
 *
 * <p><b>승격·기각(Q-15)</b>: 이상치는 사람이 정상(승격)/사기·낚시(기각)로 판정한다. 미상 항목은 딜이 없어
 * **기각만** 된다 — 승격하려면 어느 variant인지 지정해야 하는데 그 입력 경로가 아직 없다(core가 400으로
 * 막는다). 못 하는 일은 버튼으로 그리지 않는다(과대약속 금지).
 */
export function ReviewQueuePage() {
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [resolveError, setResolveError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)

  useEffect(() => {
    let live = true
    api
      .listReviewQueue()
      .then((loaded) => live && setItems(loaded))
      .catch((failure) => live && setError(describe(failure)))
    return () => {
      live = false
    }
  }, [])

  /** 처리한 항목은 PENDING에서 내려가 목록에서 사라진다 — 다시 불러 그 사실을 눈으로 확인시킨다. */
  async function resolve(id: number, action: 'promote' | 'reject') {
    setBusy(id)
    setResolveError(null)
    try {
      await (action === 'promote' ? api.promoteReviewItem(id) : api.rejectReviewItem(id))
      setItems(await api.listReviewQueue())
    } catch (failure) {
      setResolveError(describeResolve(failure))
    } finally {
      setBusy(null)
    }
  }

  return (
    <main>
      <h1>미상 큐</h1>

      <p role="note">
        매칭이 확정하지 못한 딜과 분포 하단 이상치입니다. 판단은 사람이 합니다 — 원문을 열어 확인하세요.
        <strong> 승격</strong>은 이상치를 정상 딜로 되돌려 기준가 표본에 넣고, <strong>기각</strong>은 사기·낚시로
        영구 제외합니다(재수집돼도 돌아오지 않습니다). 미상 항목은 어느 variant인지 지정할 길이 없어 기각만 됩니다.
      </p>

      {error && <p role="alert">{error}</p>}
      {resolveError && <p role="alert">{resolveError}</p>}
      {!error && items === null && <p className="loading">불러오는 중…</p>}
      {items !== null && items.length === 0 && (
        <p className="empty">대기 중인 항목이 없습니다. 매칭이 확정하지 못한 딜이 생기면 여기 쌓입니다.</p>
      )}

      {items !== null && items.length > 0 && (
        <ul aria-label="미상 큐">
          {items.map((item) => {
            const line = reviewLine(item)
            return (
              <li key={item.id} className="review-item" data-type={item.type}>
                <p className="review-reason">{line.reason}</p>
                <p className="review-detail">
                  {line.detail} ·{' '}
                  {item.sourceUrl ? (
                    <a href={item.sourceUrl} target="_blank" rel="noreferrer">
                      원문
                    </a>
                  ) : (
                    <span>원문 링크 없음</span>
                  )}
                </p>
                {/* 접힌 중복을 숨기지 않는다 — 이 숫자가 곧 "재처리 멱등이 없다"는 증거이고,
                    구간의 길이가 그 결함의 나이다(Q-27 ④). */}
                <p className="review-seen">{seenLine(item)}</p>

                <div className="review-actions">
                  {/* 미상 항목은 딜이 없어 승격할 대상 자체가 없다 — 그래서 그리지 않는다. */}
                  {item.type === 'OUTLIER_LOWER' && (
                    <button
                      type="button"
                      data-action="promote"
                      disabled={busy !== null}
                      onClick={() => resolve(item.id, 'promote')}
                    >
                      승격
                    </button>
                  )}
                  <button
                    type="button"
                    data-action="reject"
                    disabled={busy !== null}
                    onClick={() => resolve(item.id, 'reject')}
                  >
                    기각
                  </button>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
