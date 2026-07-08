package dev.hogumeter.core.domain.purchase;

/**
 * PUR-03 관찰 모드 트리거. JACKPOT(🔥 LOWER)·TARGET(목표가)·PAID_PRICE(내 구매가 하회)·RELATIVE(상대평가).
 * 상태별 on/off는 {@link PurchaseTriggers#enabledFor}.
 */
public enum PurchaseTrigger {
	JACKPOT,
	TARGET,
	PAID_PRICE,
	RELATIVE
}
