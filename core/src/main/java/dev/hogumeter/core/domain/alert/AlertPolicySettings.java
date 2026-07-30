package dev.hogumeter.core.domain.alert;

import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import dev.hogumeter.core.domain.deal.ExcludeKeywordPolicy;
import java.util.List;

/**
 * REG-03 variant별 알림 정책 — 사용자가 설정하고 저장되는 값. 순수 검증만 한다.
 *
 * <p>{@link AlertPolicy}(판정용, targetPrice + quiet hours)와 다르다: 이쪽은 <b>기간 P를 포함한 저장 단위</b>다.
 *
 * <p>{@code demand_axis_filter}는 Q-48 ②(2026-07-30) 해소 — 분리(SPLIT) 제품에서 "이 축값만 알림 받기"
 * 손잡이다. 빈 목록 = 필터 없음(모든 축값 알림, 기존 동작과 동일 — 되돌리기 쉬운 기본값).
 *
 * @param targetPrice 선택. {@code null}이면 목표가 트리거 없음. <b>0은 "미설정"이 아니라 "공짜여야 알림"</b>이라
 *     허용하지 않는다 — 둘을 섞으면 알림이 조용히 죽는다.
 * @param periodMonths 분석 기간 P. 기준가 계산에 그대로 들어간다.
 * @param quietHoursStart 방해금지 시작 시(0~23). {@code quietHoursEnd}와 함께 설정하거나 둘 다 비운다.
 * @param quietHoursEnd 방해금지 끝 시(0~23), 끝 시각 제외. 시작과 같으면 방해금지 없음({@link QuietHours}).
 * @param kDisplay 기준가 라벨 임계 K(3~10). {@code n ≥ K}면 기준가를, 아니면 사례를 낸다(BM-06).
 *     <b>표시를 바꾸는 설정이라 사용자 손잡이다</b>(확정본 §217, 원칙 4) — 산식 자체는 시스템이 고정한다.
 * @param excludeKeywords 제목이 걸리면 그 딜을 <b>전 통계에서 제외</b>하는 키워드(리퍼·벌크 등, Q-28·C-5).
 *     빈 목록 = 제외 없음. 공백은 걸러 저장한다(빈 키워드는 모든 제목에 걸린다).
 * @param demandAxisFilter 분리 제품에서 이 축값들만 알림(Q-48 ②). 빈 목록 = 필터 없음(전 축값 알림).
 *     묶음(GROUPED) 제품엔 뜻이 없다 — 딜의 축값이 늘 null이라 필터가 걸릴 대상이 없다.
 */
public record AlertPolicySettings(Long targetPrice, int periodMonths, Integer quietHoursStart,
		Integer quietHoursEnd, int kDisplay, List<String> excludeKeywords, List<String> demandAxisFilter) {

	/** 확정본 §217 기본값. 미설정 variant는 이 값으로 판정한다 — 정본은 여기 하나다(사본 금지). */
	public static final int DEFAULT_K_DISPLAY = 5;

	public AlertPolicySettings {
		// 정규화 정본은 ExcludeKeywordPolicy.normalize 하나다 — 전역 설정(Q-28 ①)과 같은 규칙을 써야
		// 두 출처를 합칠 때 공백·중복으로 어긋나지 않는다(사본을 두면 한쪽이 조용히 다른 목록을 만든다).
		// demandAxisFilter도 같은 문자열 목록 위생(공백 제거·빈 값 탈락·중복 접기)이 필요해 재사용한다 —
		// 의미는 다르지만(키워드 매칭 vs 축값 일치) 정규화 규칙 자체는 동일하다.
		excludeKeywords = ExcludeKeywordPolicy.normalize(excludeKeywords);
		demandAxisFilter = ExcludeKeywordPolicy.normalize(demandAxisFilter);
		if (periodMonths <= 0) {
			throw new InvalidBenchmarkPeriodException(periodMonths);
		}
		// DB CHECK(3~10)에 먼저 닿으면 500이 나가고 클라이언트는 무엇을 잘못했는지 알 수 없다.
		// BenchmarkParams도 같은 범위를 강제한다 — 여기서 막아야 400으로 답한다.
		if (kDisplay < 3 || kDisplay > 10) {
			throw new InvalidAlertPolicyException("kDisplay must be in 3..10: " + kDisplay);
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
