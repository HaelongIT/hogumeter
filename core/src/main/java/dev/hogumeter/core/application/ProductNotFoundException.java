package dev.hogumeter.core.application;

/** PRI/REG 대상 product 부재. 에러코드 PRI_PRODUCT_NOT_FOUND로 404 매핑(ApiExceptionHandler). */
public class ProductNotFoundException extends RuntimeException {

	public static final String CODE = "PRI_PRODUCT_NOT_FOUND";

	public ProductNotFoundException(long productId) {
		super("product not found: " + productId);
	}
}
