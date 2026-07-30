/**
 * KST 날짜 표시 — 세 번째 소비자(watch/present.ts)가 생겨 `purchase/present.ts`에서 이곳으로
 * 옮겼다(review/present.ts의 예고 그대로). 정본은 여기 하나 — 옮겨 적으면 오프셋 계산이 두 벌이
 * 되고 사본은 드리프트한다.
 */

/** KST는 UTC+9, DST가 없다. 그래서 오프셋 하나로 족하다. */
const KST_OFFSET_MS = 9 * 60 * 60 * 1000

/**
 * Instant(UTC ISO) → **KST 날짜** `YYYY-MM-DD` (OPS-03: 저장 UTC, 표시 KST).
 *
 * ISO 문자열을 `slice(0, 10)`으로 자르면 **UTC 날짜**가 나온다. `2026-07-01T20:00:00Z`는
 * 한국에서 이미 7월 2일 새벽 5시인데 화면엔 7월 1일이 뜬다. 하루가 통째로 어긋난다.
 * `toLocaleDateString`은 실행 머신의 로케일에 따라 형식이 달라지므로 쓰지 않는다.
 */
export function kstDate(instant: string): string {
  return new Date(new Date(instant).getTime() + KST_OFFSET_MS).toISOString().slice(0, 10)
}

/** 지금 이 순간의 **KST 날짜** — 폼 프리필(Q-83 ②)이 구매일 초기값으로 쓴다. */
export function todayKst(): string {
  return kstDate(new Date().toISOString())
}
