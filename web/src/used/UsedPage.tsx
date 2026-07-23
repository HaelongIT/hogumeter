import { useState } from 'react'
import { UsedComparisonPage } from './UsedComparisonPage'
import { UsedEvaluatePage } from './UsedEvaluatePage'
import { UsedSearchPage } from './UsedSearchPage'

/**
 * M2 중고 화면(USED-01~05) — 등록·평가·비교를 한 탭에 묶는다. `DecisionPage`가 신호등·기준가·
 * 주기·구매·알림정책을 한 화면에 쌓는 것과 같은 이유: 세 조각이 독립 라우트를 둘 만큼 무겁지 않고,
 * 한 흐름(검색 등록 → 매물 평가 → 비교)으로 쓰인다.
 */
export function UsedPage() {
  // 평가기의 "중고 검색" 목록은 마운트 시 한 번만 불러온다 — 방금 등록한 검색이 안 보이면
  // 사람이 새로고침해야 하는 함정이다(실제로 겪었다). 등록 성공 시 key를 바꿔 다시 불러오게 한다.
  const [evaluateRefreshKey, setEvaluateRefreshKey] = useState(0)

  return (
    <main>
      <h1>중고</h1>
      <UsedSearchPage onRegistered={() => setEvaluateRefreshKey((k) => k + 1)} />
      <UsedEvaluatePage key={evaluateRefreshKey} />
      <UsedComparisonPage />
    </main>
  )
}
