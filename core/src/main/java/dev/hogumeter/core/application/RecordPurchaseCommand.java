package dev.hogumeter.core.application;

import java.time.Instant;

/**
 * 구매 기록 명령(PUR-01). 관찰(OBSERVING) 상태로 시작하며 기록 시점 as-of 스냅샷(PUR-02)이 동결된다.
 *
 * @param demandAxisValue SPLIT 필수·GROUPED 선택(null 허용). SPLIT인데 비면 400으로 거절한다(Q-66 ③) —
 *     어느 색을 산 것인지 알아야 그 색 분포에 대고 성적을 낸다.
 * @param observationDays null/0이면 기본 90 적용
 * @param linkedDealEventId 연결 딜(선택)
 */
public record RecordPurchaseCommand(
		long variantId,
		String demandAxisValue,
		long paidPrice,
		Instant purchasedAt,
		Integer observationDays,
		Long linkedDealEventId) {
}
