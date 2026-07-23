package dev.hogumeter.core.application;

/** SEC-05: 확장이 보낸 값은 비신뢰 입력이다 — 가격이 0 이하면 조용히 저장하지 않고 거절한다(CMP-02). */
public class InvalidCoupangObservationException extends RuntimeException {

	public static final String CODE = "INVALID_COUPANG_OBSERVATION";

	public InvalidCoupangObservationException(String message) {
		super(message);
	}
}
