import { useEffect, useState } from 'react'
import type { BenchmarkView } from '../api/types'

/**
 * 계기(the meter) — **표현 전용, 정직성 게이트가 있다.** 실측값(기간최저·굿딜라인·기준가·현재가)의
 * 상대 위치만 그린다. 숫자를 포맷하지 않는다(문구는 present.ts). `aria-hidden` — 스크린리더에게는
 * 판독 문장이 정본이고, 게이지는 그 시각적 보강일 뿐이라 더 말하지 않는다.
 *
 * 게이트 = `present.ts`의 gap 거절 조건과 동일: SUFFICIENT & 기준가 있음 & 현재가>0.
 * 아니면 바늘을 지어내지 않고 "계기 미확립"이라 말한다(GRAY≠RED와 동형).
 *
 * 바늘 캡은 중립(steel)이다 — 바늘은 "현재가가 어디 있나"(사실)이고, 판정색(신호)은 위 판정 카드가
 * 낸다. 둘은 다른 축이라(딜 존재 vs 현재 소매가 위치) 섞으면 초록 판정에 빨강 구간의 바늘처럼 모순돼 보인다.
 */
export function Gauge({ view }: { view: BenchmarkView }) {
  const benchmark = view.benchmarkPrice
  const current = view.currentPrice
  const calibrated = view.tier === 'SUFFICIENT' && benchmark !== null && current > 0

  const good = view.goodDealLine
  const lowest = view.periodLowest?.price ?? null

  // 도메인: 정의된 값들의 범위에 8% 패딩. 값이 다 같으면(평평) 값의 10%를 폭으로.
  const values = calibrated
    ? [current, benchmark as number, ...(good !== null ? [good] : []), ...(lowest !== null ? [lowest] : [])]
    : []
  let lo = values.length ? Math.min(...values) : 0
  let hi = values.length ? Math.max(...values) : 1
  const base = hi - lo || Math.max(hi, 1) * 0.1
  const pad = base * 0.08
  lo -= pad
  hi += pad
  const span = hi - lo || 1
  const pos = (v: number) => Math.max(0, Math.min(100, ((v - lo) / span) * 100))

  const currentPos = calibrated ? pos(current) : 0

  // 바늘 스윕: 좌→현재가. reduced-motion이면 CSS가 전이를 꺼 즉시 놓인다.
  const [needleLeft, setNeedleLeft] = useState(0)
  useEffect(() => {
    if (!calibrated) return
    setNeedleLeft(0)
    if (typeof requestAnimationFrame !== 'function') {
      setNeedleLeft(currentPos)
      return
    }
    const id = requestAnimationFrame(() => setNeedleLeft(currentPos))
    return () => cancelAnimationFrame(id)
  }, [calibrated, currentPos])

  if (!calibrated) {
    return (
      <div className="gauge gauge--uncal" aria-hidden="true">
        <div className="gauge-bar" />
        <span className="gauge-uncal">계기 미확립 — 표본 또는 현재가가 부족합니다</span>
      </div>
    )
  }

  const bench = benchmark as number
  const splitZones = good !== null && good <= bench

  const ticks: { value: number; label: string; now?: boolean }[] = [
    ...(lowest !== null ? [{ value: lowest, label: '최저' }] : []),
    ...(good !== null ? [{ value: good, label: '굿딜' }] : []),
    { value: bench, label: '기준가' },
    { value: current, label: '현재가', now: true },
  ]

  return (
    <div className="gauge" aria-hidden="true">
      <div className="gauge-bar">
        {splitZones ? (
          <>
            <div className="gauge-zone gauge-zone--green" style={{ left: 0, width: `${pos(good as number)}%` }} />
            <div
              className="gauge-zone gauge-zone--amber"
              style={{ left: `${pos(good as number)}%`, width: `${pos(bench) - pos(good as number)}%` }}
            />
            <div className="gauge-zone gauge-zone--red" style={{ left: `${pos(bench)}%`, right: 0 }} />
          </>
        ) : (
          <>
            <div className="gauge-zone gauge-zone--neutral" style={{ left: 0, width: `${pos(bench)}%` }} />
            <div className="gauge-zone gauge-zone--red" style={{ left: `${pos(bench)}%`, right: 0 }} />
          </>
        )}
        <div className="gauge-needle" style={{ left: `${needleLeft}%` }} />
      </div>
      <div className="gauge-axis">
        {ticks.map((tick) => (
          <div
            key={tick.label}
            className={`gauge-tick${tick.now ? ' gauge-tick--now' : ''}`}
            style={{ left: `${pos(tick.value)}%` }}
          >
            <span className="gauge-tick-mark" />
            <span className="gauge-tick-label">{tick.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
