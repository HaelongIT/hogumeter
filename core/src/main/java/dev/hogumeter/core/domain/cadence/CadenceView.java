package dev.hogumeter.core.domain.cadence;

/**
 * CAD 딜 주기 결과(docs/16). 자기 분모(eventCount) 명시, "예상일" 필드 없음(예측 금지).
 *
 * @param eventCount occurrenceSet 발생 수(창 = P ∩ [observedFrom, now])
 * @param intervalMedianDays 간격 median(일). 가드 미달 시 null("주기 판단 불가")
 * @param elapsedDays 최신 발생 이후 경과일(P 무관·조회 시점). 발생 없으면 null
 * @param observedMonths 실효 관측 범위(개월)
 * @param guardMet eventCount ≥ K_display
 */
public record CadenceView(int eventCount, Long intervalMedianDays, Long elapsedDays, int observedMonths,
		boolean guardMet) {
}
