package dev.hogumeter.core.application;

/** WATCH 대상 watch_item 부재. 에러코드 WATCH_ITEM_NOT_FOUND로 404 매핑. */
public class WatchItemNotFoundException extends RuntimeException {

	public static final String CODE = "WATCH_ITEM_NOT_FOUND";

	public WatchItemNotFoundException(long watchItemId) {
		super("watch item not found: " + watchItemId);
	}
}
