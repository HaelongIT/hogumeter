import { useState } from 'react'
import { DecisionPage } from './decision/DecisionPage'
import { RegistrationPage } from './registration/RegistrationPage'
import { ReviewQueuePage } from './review/ReviewQueuePage'

// 화면이 셋뿐이라 라우터를 들이지 않는다. URL이 필요해지면 그때 넣는다.
const TABS = { decision: '지금 사도 되나', registration: '제품 등록', review: '미상 큐' } as const
type Tab = keyof typeof TABS

export function App() {
  const [tab, setTab] = useState<Tab>('decision')
  // 등록 화면이 고른 variant를 판단 화면으로 넘긴다. 등록 → 판단이 한 흐름이어야 한다.
  const [openVariantId, setOpenVariantId] = useState<number | null>(null)

  const openDecision = (variantId: number) => {
    setOpenVariantId(variantId)
    setTab('decision')
  }

  return (
    <>
      <nav aria-label="화면">
        {(Object.keys(TABS) as Tab[]).map((key) => (
          <button key={key} type="button" aria-current={tab === key} onClick={() => setTab(key)}>
            {TABS[key]}
          </button>
        ))}
      </nav>
      {tab === 'decision' && <DecisionPage initialVariantId={openVariantId} />}
      {tab === 'registration' && <RegistrationPage onOpenDecision={openDecision} />}
      {tab === 'review' && <ReviewQueuePage />}
    </>
  )
}
