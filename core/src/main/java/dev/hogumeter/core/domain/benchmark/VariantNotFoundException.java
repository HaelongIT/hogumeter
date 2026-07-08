package dev.hogumeter.core.domain.benchmark;

/** 기준가 조회 대상 variant 부재. 웹 어댑터에서 에러코드 BM_VARIANT_NOT_FOUND로 매핑(docs/benchmark/07). */
public class VariantNotFoundException extends RuntimeException {

	public static final String CODE = "BM_VARIANT_NOT_FOUND";

	public VariantNotFoundException(long variantId) {
		super("variant not found: " + variantId);
	}
}
