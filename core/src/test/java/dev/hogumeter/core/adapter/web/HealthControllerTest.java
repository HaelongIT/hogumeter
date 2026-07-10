package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * DB 프로브를 주입해 IO 없이 검증한다 — compose healthcheck가 읽는 것은 <b>상태 코드</b>이고,
 * 사람이 읽는 것은 <b>본문</b>이다. 둘이 어긋나면 "unhealthy인데 왜인지 모름"이 된다.
 */
class HealthControllerTest {

	@Test
	void aliveWithAReachableDatabaseIsTwoHundredAndUp() {
		ResponseEntity<HealthReport> response = new HealthController(() -> {}).health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo("UP");
		assertThat(response.getBody().components().get("db").status()).isEqualTo("UP");
	}

	/**
	 * 503이어야 한다. 200을 주면 compose가 죽은 스택을 healthy로 표시하고, 그 판정 위에 올린
	 * `depends_on: service_healthy`가 전부 거짓이 된다.
	 */
	@Test
	void unreachableDatabaseIsFiveOhThreeAndNamesTheDeadComponent() {
		HealthController controller = new HealthController(() -> {
			throw new SQLException("connection refused");
		});

		ResponseEntity<HealthReport> response = controller.health();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody().status()).isEqualTo("DOWN");
		assertThat(response.getBody().components().get("db").error()).isEqualTo("SQLException");
	}

	/**
	 * 헬스 엔드포인트가 예외를 던지면 500이 나가고, 본문이 없어 <b>무엇이 죽었는지 알 수 없다</b>.
	 * 프로브가 무엇을 던지든(RuntimeException·Error 아닌 것 전부) 판정으로 바꾼다.
	 */
	@Test
	void neverThrows() {
		HealthController controller = new HealthController(() -> {
			throw new IllegalStateException("pool closed");
		});

		assertThat(controller.health().getBody().components().get("db").error())
			.isEqualTo("IllegalStateException");
	}
}
