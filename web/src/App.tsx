import { useState } from 'react'
import type { BoughtPrefill } from './api/types'
import { ThemeToggle } from './components/ThemeToggle'
import { DecisionPage } from './decision/DecisionPage'
import { PriorityPage } from './priority/PriorityPage'
import type { PurchasePrefill } from './purchase/PurchasePanel'
import { RegistrationPage } from './registration/RegistrationPage'
import { ReviewQueuePage } from './review/ReviewQueuePage'
import { SettingsPage } from './settings/SettingsPage'
import { UsedPage } from './used/UsedPage'
import { WatchPage } from './watch/WatchPage'

// 라우터를 들이지 않는다 — 1인용 규모라 탭으로 족하다. URL이 필요해지면 그때 넣는다.
const TABS = {
  decision: '지금 사도 되나',
  registration: '제품 등록',
  used: '중고',
  priority: '우선순위',
  watch: '딜 보관함',
  review: '미상 큐',
  settings: '설정',
} as const
type Tab = keyof typeof TABS

export function App() {
  const [tab, setTab] = useState<Tab>('decision')
  // 등록 화면이 고른 variant를 판단 화면으로 넘긴다. 등록 → 판단이 한 흐름이어야 한다.
  const [openVariantId, setOpenVariantId] = useState<number | null>(null)
  // WATCH [샀어요]가 실어 온 구매 기록 프리필(Q-83 ②) — DecisionPage → PurchasePanel로 그대로 흘려보낸다.
  const [purchasePrefill, setPurchasePrefill] = useState<PurchasePrefill | null>(null)

  const openDecision = (variantId: number) => {
    setOpenVariantId(variantId)
    setPurchasePrefill(null) // 등록 경유는 구매 프리필과 무관 — 이전 핀의 프리필이 새지 않게 지운다
    setTab('decision')
  }

  /** WATCH(Q-83 ②) [샀어요] → 그 딜의 variant 판단 화면으로 이동 + 구매 기록 폼 프리필. */
  const openDecisionForPurchase = (prefill: BoughtPrefill) => {
    if (prefill.variantId === null) return // WatchPage가 이미 막지만 방어적으로 한 번 더
    setOpenVariantId(prefill.variantId)
    setPurchasePrefill({
      dealEventId: prefill.dealEventId,
      dealPrice: prefill.dealPrice,
      appliedConditions: prefill.appliedConditions,
    })
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
      {tab === 'decision' && <DecisionPage initialVariantId={openVariantId} purchasePrefill={purchasePrefill} />}
      {tab === 'registration' && <RegistrationPage onOpenDecision={openDecision} />}
      {tab === 'used' && <UsedPage />}
      {tab === 'priority' && <PriorityPage />}
      {tab === 'watch' && <WatchPage onBought={openDecisionForPurchase} />}
      {tab === 'review' && <ReviewQueuePage />}
      {tab === 'settings' && <SettingsPage />}
    </div>
  )
}
