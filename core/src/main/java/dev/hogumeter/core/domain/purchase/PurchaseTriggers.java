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

	/**
	 * 이 variant에 걸린 관찰들을 놓고 트리거가 켜져 있는가("복수 관찰 = 트리거 열별 OR"). 관찰이
	 * 하나라도 이 트리거를 허용하면 켜진다 — 전부 ARCHIVED일 때만 꺼진다.
	 *
	 * <p><b>관찰이 아예 없으면 항상 켜진다</b> — 이 매트릭스는 "구매 후 알림을 어떻게 계속할까"를 다룰
	 * 뿐이라, 관찰 자체가 없는 딜의 판정(AL-02)을 이 표가 막을 이유가 없다.
	 */
	public static boolean isEnabled(PurchaseTrigger trigger, List<Purchase> purchases) {
		if (purchases.isEmpty()) {
			return true;
		}
		return purchases.stream().anyMatch(p -> enabledFor(p.state()).contains(trigger));
	}
}
