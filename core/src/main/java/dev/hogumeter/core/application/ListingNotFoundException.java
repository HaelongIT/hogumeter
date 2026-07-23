package dev.hogumeter.core.application;

/** 메모·축값 승격 대상 매물이 없다(USED-05). */
public class ListingNotFoundException extends RuntimeException {

	public static final String CODE = "LISTING_NOT_FOUND";

	public ListingNotFoundException(long listingId) {
		super("매물을 찾을 수 없습니다: #" + listingId);
	}
}
