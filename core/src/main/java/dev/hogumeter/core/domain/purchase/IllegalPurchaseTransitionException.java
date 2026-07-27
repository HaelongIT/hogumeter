package dev.hogumeter.core.domain.purchase;

/** 허용되지 않은 Purchase 상태 전이 시도(PUR-01). */
public class IllegalPurchaseTransitionException extends RuntimeException {

	public static final String CODE = "PUR_ILLEGAL_TRANSITION";

	public IllegalPurchaseTransitionException(PurchaseState from, PurchaseState to) {
		super("illegal purchase transition: " + from + " → " + to);
	}
}
