import { useEffect, useState } from 'react'
import { ApiFailure, api } from '../api/client'
import type { ReviewQueueItem } from '../api/types'
import { reviewLine, seenLine } from './present'

const describe = (failure: unknown) =>
  failure instanceof ApiFailure
    ? `미상 큐를 불러오지 못했습니다 (${failure.code})`
    : '미상 큐를 불러오지 못했습니다 — core가 떠 있는지 확인하세요.'

/**
 * 미상 큐(읽기 전용). 매칭이 확정하지 못한 딜과 분포 하단 이상치가 여기 쌓인다.
 *
 * <p>이 화면이 생기기 전까지 `review_queue_item`은 쓰이기만 하고 아무도 읽지 않았다 — 매칭이 무엇을
 * 놓치는지 볼 방법이 없었다. 놓침을 허용하는 시스템에서 놓친 것을 볼 수 없다면 그건 유실이다.
 *
 * <p>승격·기각 버튼은 **그리지 않는다.** 그 REST가 없다(docs/91 Q-15). 못 하는 일을 버튼으로 그리면
 * 사용자는 눌러 보고 나서야 안다(과대약속 금지).
 */
export function ReviewQueuePage() {
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <main>
      <h1>미상 큐</h1>

      <p role="note">
        매칭이 확정하지 못한 딜과 분포 하단 이상치입니다. 판단은 사람이 합니다 — 원문을 열어 확인하세요.
        <strong> 승격·기각은 아직 여기서 할 수 없습니다</strong>(텔레그램 인라인 버튼이 붙으면 열립니다).
      </p>

      {error && <p role="alert">{error}</p>}
      {!error && items === null && <p>불러오는 중…</p>}
      {items !== null && items.length === 0 && <p>대기 중인 항목이 없습니다.</p>}

      {items !== null && items.length > 0 && (
        <ul aria-label="미상 큐">
          {items.map((item) => {
            const line = reviewLine(item)
            return (
              <li key={item.id}>
                <p>{line.reason}</p>
                <p>
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
                <p>{seenLine(item)}</p>
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
