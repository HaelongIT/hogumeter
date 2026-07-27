package dev.hogumeter.core.application;

/** 대상 purchase 부재. 에러코드 PURCHASE_NOT_FOUND로 404 매핑. */
public class PurchaseNotFoundException extends RuntimeException {

	public static final String CODE = "PURCHASE_NOT_FOUND";

	public PurchaseNotFoundException(long purchaseId) {
		super("purchase not found: " + purchaseId);
	}
}
