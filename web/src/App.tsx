import { useState } from 'react'
import { DecisionPage } from './decision/DecisionPage'
import { RegistrationPage } from './registration/RegistrationPage'

// 화면이 둘뿐이라 라우터를 들이지 않는다. URL이 필요해지면 그때 넣는다.
const TABS = { decision: '지금 사도 되나', registration: '제품 등록' } as const
type Tab = keyof typeof TABS

export function App() {
  const [tab, setTab] = useState<Tab>('decision')

  return (
    <>
      <nav aria-label="화면">
        {(Object.keys(TABS) as Tab[]).map((key) => (
          <button key={key} type="button" aria-current={tab === key} onClick={() => setTab(key)}>
            {TABS[key]}
          </button>
        ))}
      </nav>
      {tab === 'decision' ? <DecisionPage /> : <RegistrationPage />}
    </>
  )
}
