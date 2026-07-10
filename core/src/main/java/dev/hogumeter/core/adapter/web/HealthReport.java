package dev.hogumeter.core.adapter.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * OBS-04 컴포넌트별 헬스 응답 — 순수 집계. 하나라도 DOWN이면 전체 DOWN.
 *
 * <pre>
 * {"status":"UP",  "components":{"db":{"status":"UP"}}}
 * {"status":"DOWN","components":{"db":{"status":"DOWN","error":"SQLException"}}}
 * </pre>
 */
public record HealthReport(String status, Map<String, Component> components) {

	public static final String UP = "UP";
	public static final String DOWN = "DOWN";

	/** 컴포넌트 하나의 상태. 건강하면 `error`는 응답에서 아예 빠진다. */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Component(String status, String error) {

		public static Component up() {
			return new Component(UP, null);
		}

		/**
		 * 예외의 <b>타입 이름만</b> 싣는다. JDBC 예외 메시지는 접속 URL·사용자명을, 드라이버에 따라
		 * 자격증명까지 담는다. 헬스 응답은 인증 없이 노출되는 것이 정상이므로 메시지를 그대로 흘리면
		 * 관측이 곧 유출이 된다(SEC-01). 상세는 로그에 남고, 로그는 인증 뒤에 있다.
		 */
		public static Component down(Throwable cause) {
			return new Component(DOWN, cause.getClass().getSimpleName());
		}
	}

	/**
	 * @throws IllegalArgumentException 검사한 컴포넌트가 하나도 없을 때. 빈 집합의 allMatch는 true라
	 *     아무것도 확인하지 않고 "건강함"을 반환하게 된다 — 헬스체크에서 가장 위험한 거짓말이다.
	 */
	public static HealthReport of(Map<String, Component> components) {
		if (components.isEmpty()) {
			throw new IllegalArgumentException("검사한 컴포넌트가 없습니다 — 빈 헬스 리포트는 UP이 아니라 오류입니다");
		}
		boolean allUp = components.values().stream().allMatch(component -> UP.equals(component.status()));
		return new HealthReport(allUp ? UP : DOWN, Map.copyOf(components));
	}

	public boolean healthy() {
		return UP.equals(status);
	}
}
