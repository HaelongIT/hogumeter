package dev.hogumeter.core.application;

import java.time.Instant;

/**
 * 구매 기록 명령(PUR-01). 관찰(OBSERVING) 상태로 시작하며 기록 시점 as-of 스냅샷(PUR-02)이 동결된다.
 *
 * @param demandAxisValue SPLIT 필수·GROUPED 선택(null 허용) — 필수성 검증은 후속
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
