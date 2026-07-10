package dev.hogumeter.core.domain.alert;

import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;

/**
 * REG-03 variant별 알림 정책 — 사용자가 설정하고 저장되는 값. 순수 검증만 한다.
 *
 * <p>{@link AlertPolicy}(판정용, targetPrice + quiet hours)와 다르다: 이쪽은 <b>기간 P를 포함한 저장 단위</b>다.
 *
 * <p>DB에는 {@code k_display}·{@code exclude_keywords}·{@code demand_axis_filter} 컬럼도 있지만
 * {@code AlertPolicyEntity}가 매핑하지 않아 여기서 다루지 않는다(docs/91 Q-48).
 *
 * @param targetPrice 선택. {@code null}이면 목표가 트리거 없음. <b>0은 "미설정"이 아니라 "공짜여야 알림"</b>이라
 *     허용하지 않는다 — 둘을 섞으면 알림이 조용히 죽는다.
 * @param periodMonths 분석 기간 P. 기준가 계산에 그대로 들어간다.
 * @param quietHoursStart 방해금지 시작 시(0~23). {@code quietHoursEnd}와 함께 설정하거나 둘 다 비운다.
 * @param quietHoursEnd 방해금지 끝 시(0~23), 끝 시각 제외. 시작과 같으면 방해금지 없음({@link QuietHours}).
 */
public record AlertPolicySettings(Long targetPrice, int periodMonths, Integer quietHoursStart,
		Integer quietHoursEnd) {

	public AlertPolicySettings {
		if (periodMonths <= 0) {
			throw new InvalidBenchmarkPeriodException(periodMonths);
		}
		if (targetPrice != null && targetPrice <= 0) {
			throw new InvalidAlertPolicyException("targetPrice must be positive when set: " + targetPrice);
		}
		if ((quietHoursStart == null) != (quietHoursEnd == null)) {
			// 한쪽만 설정하면 QuietHours가 조용히 "방해금지 없음"으로 읽는다 — 설정한 줄 알고 있는데.
			throw new InvalidAlertPolicyException("quiet hours must be set together or not at all");
		}
		requireHourOfDay(quietHoursStart, "quietHoursStart");
		requireHourOfDay(quietHoursEnd, "quietHoursEnd");
	}

	/** DB CHECK(0~23)에 먼저 닿으면 500이 나가고 클라이언트는 무엇을 잘못했는지 알 수 없다. */
	private static void requireHourOfDay(Integer hour, String field) {
		if (hour != null && (hour < 0 || hour > 23)) {
			throw new InvalidAlertPolicyException(field + " must be an hour of day (0-23): " + hour);
		}
	}
}
