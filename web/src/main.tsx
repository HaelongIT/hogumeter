import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RegistrationPage } from './registration/RegistrationPage'

const root = document.getElementById('root')
if (!root) throw new Error('#root 를 찾지 못했습니다')

createRoot(root).render(
  <StrictMode>
    <RegistrationPage />
  </StrictMode>,
)
