package dev.hogumeter.core.application;

/**
 * 구매 기록 명령(PUR-01)이 필수 값을 빠뜨렸거나 말이 안 되는 값일 때(BE-11·BE-12·BE-20, 코드리뷰
 * 20260806) — {@code purchasedAt} 누락(NPE 방지), {@code paidPrice} 0/음수(0으로 나누기·성적표
 * 오염 방지). 구매 기록은 수정 API가 없어 되돌리기 어려우므로 저장 전에 400으로 막는다.
 */
public class InvalidPurchaseCommandException extends RuntimeException {

	public static final String CODE = "PUR_INVALID_COMMAND";

	public InvalidPurchaseCommandException(String message) {
		super(message);
	}
}
