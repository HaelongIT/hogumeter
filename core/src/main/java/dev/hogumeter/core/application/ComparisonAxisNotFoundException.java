package dev.hogumeter.core.application;

/** 값을 승격하려는 비교축이 없다(USED-05 AC-17) — 축은 먼저 정의돼야 값이 붙는다. */
public class ComparisonAxisNotFoundException extends RuntimeException {

	public static final String CODE = "COMPARISON_AXIS_NOT_FOUND";

	public ComparisonAxisNotFoundException(long axisId) {
		super("비교축을 찾을 수 없습니다: #" + axisId);
	}
}
