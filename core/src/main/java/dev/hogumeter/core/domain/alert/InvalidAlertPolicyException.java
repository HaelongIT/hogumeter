package dev.hogumeter.core.domain.alert;

/**
 * REG-03 알림 정책 입력이 도메인 규칙을 어겼을 때. 웹 어댑터가 에러코드 {@code REG_INVALID_ALERT_POLICY}로
 * 400에 매핑한다(docs/benchmark/07). 순수 도메인은 이 예외만 던지고 HTTP 관심사는 어댑터가 진다.
 *
 * <p>기간 P는 이 예외를 쓰지 않는다 — 기준가 계산과 <b>같은 값</b>이므로
 * {@code InvalidBenchmarkPeriodException}(BM_INVALID_PERIOD)을 재사용한다. 같은 개념에 코드를 둘 만들면
 * 클라이언트가 두 갈래로 분기한다.
 */
public class InvalidAlertPolicyException extends RuntimeException {

	public static final String CODE = "REG_INVALID_ALERT_POLICY";

	public InvalidAlertPolicyException(String message) {
		super(message);
	}
}
