package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.adapter.web.HealthReport.Component;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * OBS-04 헬스 집계 — 순수 함수. 어떤 컴포넌트 하나라도 DOWN이면 전체가 DOWN이다.
 *
 * <p>헬스 응답은 운영자가 "무엇이 죽었나"를 묻는 유일한 창구다. 그래서 두 가지를 못박는다 —
 * 하나라도 죽으면 전체를 살아있다고 말하지 않을 것, 그리고 죽은 이유를 말하되 <b>비밀은 말하지 않을 것</b>.
 */
class HealthReportTest {

	@Test
	void allComponentsUpMeansUp() {
		HealthReport report = HealthReport.of(Map.of("db", Component.up()));

		assertThat(report.status()).isEqualTo("UP");
		assertThat(report.healthy()).isTrue();
	}

	@Test
	void oneComponentDownDragsTheWholeReportDown() {
		Map<String, Component> components = new LinkedHashMap<>();
		components.put("db", Component.down(new SQLException("boom")));
		components.put("cache", Component.up());

		HealthReport report = HealthReport.of(components);

		assertThat(report.status()).isEqualTo("DOWN");
		assertThat(report.healthy()).isFalse();
		assertThat(report.components().get("cache").status()).isEqualTo("UP");
	}

	/**
	 * 빈 집합에 대한 allMatch는 true다 — 아무것도 검사하지 않고 "건강함"을 반환하게 된다.
	 * 그건 값 없음을 값으로 표현하는 것이고, 헬스체크에서는 가장 위험한 종류의 거짓말이다.
	 */
	@Test
	void refusesToCallNothingHealthy() {
		assertThatThrownBy(() -> HealthReport.of(Map.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * 예외 <b>메시지</b>는 싣지 않는다. JDBC 예외 메시지는 접속 URL과 사용자명을 그대로 담고
	 * (드라이버에 따라 비밀번호까지), 헬스 엔드포인트는 인증 없이 노출되는 것이 정상이다.
	 */
	@Test
	void reportsWhyItIsDownWithoutLeakingTheReason() {
		SQLException cause = new SQLException("FATAL: password authentication failed for user \"hogumeter\"");

		Component component = Component.down(cause);

		assertThat(component.status()).isEqualTo("DOWN");
		assertThat(component.error()).isEqualTo("SQLException");
		assertThat(component.error()).doesNotContain("password");
	}

	@Test
	void healthyComponentHasNoError() {
		assertThat(Component.up().error()).isNull();
	}
}
