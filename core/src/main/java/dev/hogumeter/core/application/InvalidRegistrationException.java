package dev.hogumeter.core.application;

/**
 * 등록 명령이 서버측 불변을 어겼다(Q-49). 클라이언트 검증(`buildCommand`)은 편의일 뿐 — curl로 직접 치면
 * 통과한다. 서버가 막지 않으면 빈 이름은 DB NOT NULL을 뚫거나(500) variant 없는 제품이 저장된다.
 * 에러는 {@code {code, message}}로 400을 낸다(ApiExceptionHandler).
 */
public class InvalidRegistrationException extends RuntimeException {

	public static final String CODE = "REG_INVALID_PRODUCT";

	public InvalidRegistrationException(String message) {
		super(message);
	}
}
