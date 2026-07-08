package dev.hogumeter.core.domain.purchase;

import java.util.Map;
import java.util.Set;

/**
 * PUR-01 Purchase 관찰 상태기계(docs/15). OBSERVING→REPORT_PENDING(관찰 만료)→CLOSED(성적표 발급)→
 * ARCHIVED(아카이브). ARCHIVED→OBSERVING 재활성(수동 복원·재구매). 나머지 전이 거부.
 */
public enum PurchaseState {
	OBSERVING,
	REPORT_PENDING,
	CLOSED,
	ARCHIVED;

	private static final Map<PurchaseState, Set<PurchaseState>> ALLOWED = Map.of(
			OBSERVING, Set.of(REPORT_PENDING),
			REPORT_PENDING, Set.of(CLOSED),
			CLOSED, Set.of(ARCHIVED),
			ARCHIVED, Set.of(OBSERVING));

	public boolean canTransitionTo(PurchaseState target) {
		return ALLOWED.get(this).contains(target);
	}

	public PurchaseState transitionTo(PurchaseState target) {
		if (!canTransitionTo(target)) {
			throw new IllegalPurchaseTransitionException(this, target);
		}
		return target;
	}
}
