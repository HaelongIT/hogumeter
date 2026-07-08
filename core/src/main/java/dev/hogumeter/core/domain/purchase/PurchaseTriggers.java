package dev.hogumeter.core.domain.purchase;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * PUR-03 상태 × 트리거 매트릭스(순수). 🔥·목표가는 ARCHIVED만 off / paidPrice 하회는 OBSERVING만 /
 * 상대평가는 CLOSED만. paidPrice 하회는 "&lt;" 경계·복수 관찰 OR·서열 최하위(발송 단위는 AL 소관).
 * 상대평가 발화(타 관찰 비교)는 후속(docs/91 Q-31).
 */
public final class PurchaseTriggers {

	private PurchaseTriggers() {
	}

	public static Set<PurchaseTrigger> enabledFor(PurchaseState state) {
		return switch (state) {
			case OBSERVING -> EnumSet.of(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET, PurchaseTrigger.PAID_PRICE);
			case REPORT_PENDING -> EnumSet.of(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET);
			case CLOSED -> EnumSet.of(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET, PurchaseTrigger.RELATIVE);
			case ARCHIVED -> EnumSet.noneOf(PurchaseTrigger.class);
		};
	}

	/** paidPrice 하회 트리거: 어느 활성(OBSERVING) 관찰의 paidPrice보다든 미만이면 발화("&lt;" 경계, OR). */
	public static boolean paidPriceTriggerFires(long dealPrice, List<Purchase> purchases) {
		return purchases.stream()
				.filter(p -> p.state() == PurchaseState.OBSERVING)
				.anyMatch(p -> dealPrice < p.paidPrice());
	}
}
