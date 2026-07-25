package dev.hogumeter.core.application;

/** WATCH 대상 deal_event 부재. 에러코드 WATCH_DEAL_NOT_FOUND로 404 매핑(ApiExceptionHandler). */
public class DealEventNotFoundException extends RuntimeException {

	public static final String CODE = "WATCH_DEAL_NOT_FOUND";

	public DealEventNotFoundException(long dealEventId) {
		super("deal event not found: " + dealEventId);
	}
}
