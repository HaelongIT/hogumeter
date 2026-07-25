package dev.hogumeter.core.application;

/** WATCH 유일성(docs/17 "딜당 활성 핀 1개") 위반. 에러코드 WATCH_ALREADY_PINNED로 409 매핑. */
public class DealAlreadyPinnedException extends RuntimeException {

	public static final String CODE = "WATCH_ALREADY_PINNED";

	public DealAlreadyPinnedException(long dealEventId) {
		super("deal already has an active pin: " + dealEventId);
	}
}
