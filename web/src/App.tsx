import { useState } from 'react'
import { ThemeToggle } from './components/ThemeToggle'
import { DecisionPage } from './decision/DecisionPage'
import { RegistrationPage } from './registration/RegistrationPage'
import { ReviewQueuePage } from './review/ReviewQueuePage'
import { SettingsPage } from './settings/SettingsPage'

// 화면이 넷뿐이라 라우터를 들이지 않는다. URL이 필요해지면 그때 넣는다.
const TABS = { decision: '지금 사도 되나', registration: '제품 등록', review: '미상 큐', settings: '설정' } as const
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
    <div className="shell">
      <header className="topbar">
        <div className="topbar-row">
          <div className="brand">
            <span className="wordmark">호구미터</span>
            <span className="brand-tag">HOGU·METER</span>
          </div>
          <ThemeToggle />
        </div>
        <nav aria-label="화면" className="tabs">
          {(Object.keys(TABS) as Tab[]).map((key) => (
            <button key={key} type="button" aria-current={tab === key} onClick={() => setTab(key)}>
              {TABS[key]}
            </button>
          ))}
        </nav>
      </header>
      {tab === 'decision' && <DecisionPage initialVariantId={openVariantId} />}
      {tab === 'registration' && <RegistrationPage onOpenDecision={openDecision} />}
      {tab === 'review' && <ReviewQueuePage />}
      {tab === 'settings' && <SettingsPage />}
    </div>
  )
}
