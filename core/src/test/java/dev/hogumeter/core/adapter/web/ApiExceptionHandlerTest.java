package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * BE-15(코드리뷰 20260806) — 미매핑 예외(NPE·DataIntegrityViolationException 등)가 각 컨트롤러
 * 진입점의 우연에 따라 Spring 기본 오류 응답으로 떨어지지 않고, 이 어드바이스의 최종 방어선을 거쳐
 * 항상 {@code {code, message}} 계약을 지키게 한다. 근본 수정은 발생 지점에서 400/404로 막는 것이
 * 우선이다(BE-11~14) — 이건 그 방어선을 벗어난 나머지를 위한 회귀 안전망이다.
 *
 * <p>HealthController와 같은 원칙(SEC-01)을 따른다 — 예외 메시지(SQL·연결 정보를 담을 수 있다)를
 * 그대로 노출하지 않고 예외 타입 이름만 담는다.
 */
class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void unmappedNullPointerExceptionGetsAStableEnvelopeNotARawMessage() {
		ApiExceptionHandler.ApiError error = handler.unmapped(new NullPointerException("internal detail"));

		assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
		assertThat(error.message()).doesNotContain("internal detail"); // 원인 메시지를 그대로 흘리지 않는다
		assertThat(error.message()).contains("NullPointerException");
	}

	@Test
	void unmappedDataIntegrityViolationDoesNotLeakSqlDetails() {
		ApiExceptionHandler.ApiError error = handler.unmapped(
				new DataIntegrityViolationException("insert into ... violates constraint ..."));

		assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
		assertThat(error.message()).doesNotContain("constraint");
		assertThat(error.message()).contains("DataIntegrityViolationException");
	}
}
