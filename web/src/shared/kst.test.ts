import { describe, expect, it } from 'vitest'
import { kstDate, todayKst } from './kst'

describe('kstDate — 저장은 UTC, 표시는 KST (OPS-03)', () => {
  it('UTC 저녁은 KST로 다음 날이다 — ISO 문자열을 그냥 자르면 하루가 어긋난다', () => {
    expect(kstDate('2026-07-01T20:00:00Z')).toBe('2026-07-02')
    expect('2026-07-01T20:00:00Z'.slice(0, 10)).toBe('2026-07-01') // 우리가 고치려는 그 버그
  })

  it('KST 자정 경계 (15:00Z)', () => {
    expect(kstDate('2026-07-01T14:59:59Z')).toBe('2026-07-01')
    expect(kstDate('2026-07-01T15:00:00Z')).toBe('2026-07-02')
  })

  it('우리 입력 경로가 쓰는 23:59 KST는 그대로 그 날이다', () => {
    // buildPurchaseCommand가 만드는 값(2026-07-01T23:59+09:00 = 14:59Z)
    expect(kstDate('2026-07-01T14:59:00.000Z')).toBe('2026-07-01')
  })

  it('연말 경계에서도 어긋나지 않는다', () => {
    expect(kstDate('2025-12-31T15:00:00Z')).toBe('2026-01-01')
  })

  it('실행 머신의 타임존에 의존하지 않는다 — 로컬 해석과 다를 수 있어야 한다', () => {
    // 로컬이 UTC든 KST든 결과가 같아야 한다. 오프셋을 명시해 계산하기 때문이다.
    const instant = '2026-07-01T20:00:00Z'
    expect(kstDate(instant)).toBe('2026-07-02')
    expect(kstDate(instant)).not.toBe(new Date(instant).toISOString().slice(0, 10))
  })
})

describe('todayKst — 지금 이 순간의 KST 날짜(Q-83 ② 폼 프리필용)', () => {
  it('kstDate(지금)과 같은 날을 낸다 — 실제 벽시계를 같은 방식으로 변환할 뿐이다', () => {
    expect(todayKst()).toBe(kstDate(new Date().toISOString()))
  })

  it('YYYY-MM-DD 형식이다', () => {
    expect(todayKst()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})
