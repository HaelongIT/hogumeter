package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.adapter.web.ApiExceptionHandler.ApiError;
import dev.hogumeter.core.domain.alert.InvalidAlertPolicyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REG-03 정책 입력 오류 → 400. 응답 모양은 {@link ApiExceptionHandler}의 {@code {code, message}}를 그대로 쓴다.
 *
 * <p>별도 advice인 이유는 하나뿐이다 — {@code ApiExceptionHandler}는 다른 개발자가 소유한 기존 파일이라
 * 손대지 않는다. Spring은 여러 {@code @RestControllerAdvice}를 합쳐서 적용한다. 소유권이 정리되면
 * 이 클래스는 그쪽으로 합쳐야 한다(핸들러가 흩어지면 어디를 봐야 할지 알 수 없다).
 */
@RestControllerAdvice
public class AlertPolicyExceptionHandler {

	@ExceptionHandler(InvalidAlertPolicyException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ApiError invalidAlertPolicy(InvalidAlertPolicyException e) {
		return new ApiError(InvalidAlertPolicyException.CODE, e.getMessage());
	}
}
