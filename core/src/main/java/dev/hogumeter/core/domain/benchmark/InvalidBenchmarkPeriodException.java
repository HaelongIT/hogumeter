package dev.hogumeter.core.domain.benchmark;

/**
 * 조회 기간(periodMonths)이 0 이하일 때. 웹 어댑터에서 에러코드 BM_INVALID_PERIOD로 매핑
 * (docs/benchmark/07). 순수 도메인은 이 예외만 던지고 HTTP 관심사는 어댑터가 진다.
 */
public class InvalidBenchmarkPeriodException extends RuntimeException {

	public static final String CODE = "BM_INVALID_PERIOD";

	public InvalidBenchmarkPeriodException(int periodMonths) {
		super("periodMonths must be positive: " + periodMonths);
	}
}
