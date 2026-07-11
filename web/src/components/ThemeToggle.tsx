import { useEffect, useState } from 'react'

type Theme = 'light' | 'dark'

const KEY = 'hogu-theme'

/** 저장된 명시 선택만 읽는다. 없으면 null — 그때 색은 CSS의 OS 선호가 정한다(matchMedia 안 씀 → 테스트 안전). */
function stored(): Theme | null {
  try {
    const value = localStorage.getItem(KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

/** 라이트·다크 토글. 명시 선택은 data-theme + localStorage에 못 박아 다음 방문에도 이긴다. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme | null>(stored)

  useEffect(() => {
    if (theme === null) return
    document.documentElement.setAttribute('data-theme', theme)
    try {
      localStorage.setItem(KEY, theme)
    } catch {
      /* 저장 불가여도 현재 세션엔 적용된다 */
    }
  }, [theme])

  // 명시 선택이 없으면 아이콘은 기본 정체성(다크)을 가정한다 — 첫 클릭이 반대로 넘긴다.
  const current: Theme = theme ?? 'dark'
  const next: Theme = current === 'dark' ? 'light' : 'dark'

  return (
    <button
      type="button"
      className="theme-toggle"
      aria-label={`${next === 'light' ? '라이트' : '다크'} 테마로 전환`}
      onClick={() => setTheme(next)}
    >
      <span aria-hidden="true">{current === 'dark' ? '☾' : '☀'}</span>
    </button>
  )
}
