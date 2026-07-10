package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.adapter.web.HealthReport.Component;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OBS-04 전용 헬스 엔드포인트. 그전까지 compose는 `/api/v1/products`를 헬스체크로 썼다 —
 * 목록이 커지면 헬스체크가 비싸지고, "core는 살아 있는데 DB만 죽음"을 구분하지 못했다(Q-50).
 *
 * <p>DB 프로브는 커넥션 풀의 유효성 검사(`Connection.isValid`)다. 쿼리를 돌리지 않으므로 데이터 양과
 * 무관하게 상수 시간이고, 그러면서도 <b>DB까지 실제로 닿는다</b>.
 */
@RestController
public class HealthController {

	/** 프로브가 이보다 오래 걸리면 DB는 사실상 죽은 것이다. compose의 `timeout: 3s`보다 짧게 둔다. */
	private static final int PROBE_TIMEOUT_SECONDS = 2;

	private final Probe db;

	@Autowired
	public HealthController(DataSource dataSource) {
		this(() -> {
			try (Connection connection = dataSource.getConnection()) {
				if (!connection.isValid(PROBE_TIMEOUT_SECONDS)) {
					throw new SQLException("connection is not valid");
				}
			}
		});
	}

	HealthController(Probe db) {
		this.db = db;
	}

	@GetMapping("/api/v1/health")
	public ResponseEntity<HealthReport> health() {
		HealthReport report = HealthReport.of(Map.of("db", probe(db)));
		return ResponseEntity
			.status(report.healthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
			.body(report);
	}

	/**
	 * 어떤 예외도 밖으로 내보내지 않는다. 헬스 엔드포인트가 던지면 500 + 본문 없음이 되어,
	 * 정작 <b>무엇이 죽었는지</b>를 물으려고 만든 창구가 그 답을 못 준다.
	 */
	private static Component probe(Probe probe) {
		try {
			probe.check();
			return Component.up();
		}
		catch (Exception failure) {
			return Component.down(failure);
		}
	}

	/** 테스트 이음새 — IO 없이 판정(상태 코드·본문)을 검증한다. */
	@FunctionalInterface
	interface Probe {
		void check() throws Exception;
	}
}
