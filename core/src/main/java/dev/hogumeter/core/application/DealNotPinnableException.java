package dev.hogumeter.core.application;

/** WATCH 핀 자격 미달(docs/17 — 표시된 딜만). 에러코드 WATCH_DEAL_NOT_PINNABLE로 400 매핑. */
public class DealNotPinnableException extends RuntimeException {

	public static final String CODE = "WATCH_DEAL_NOT_PINNABLE";

	public DealNotPinnableException(long dealEventId) {
		super("deal is not eligible to pin (ended, outlier, or excluded): " + dealEventId);
	}
}
