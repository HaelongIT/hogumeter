package dev.hogumeter.core.domain.purchase;

import java.math.BigDecimal;

/**
 * PUR-05 관찰 문맥(순수 값) — 상세 화면 Purchase별 1줄. 세 모드 배타:
 * <ul>
 * <li>{@code ACTIVE_DEAL}: 활성 딜 최저 priceLast + 내 구매가 상회분(양수=상회 구매)</li>
 * <li>{@code NO_ACTIVE_DEAL}: 활성 딜 없음 → 관찰 D일차 + 내 구매가보다 싼 기회 건수(pricingSet, priceMin&lt;paid)</li>
 * <li>{@code REPORT_PENDING}: 성적 집계 중</li>
 * </ul>
 * 해당 모드 밖 필드는 null.
 */
public record ObservationContext(
		Mode mode,
		Long activeLowestPriceLast,
		Long overpaidWon,
		BigDecimal overpaidPct,
		Integer observationDay,
		Integer cheaperChanceCount) {

	public enum Mode {
		ACTIVE_DEAL,
		NO_ACTIVE_DEAL,
		REPORT_PENDING
	}

	static ObservationContext pending() {
		return new ObservationContext(Mode.REPORT_PENDING, null, null, null, null, null);
	}

	static ObservationContext activeDeal(long lowestPriceLast, long overpaidWon, BigDecimal overpaidPct) {
		return new ObservationContext(Mode.ACTIVE_DEAL, lowestPriceLast, overpaidWon, overpaidPct, null, null);
	}

	static ObservationContext noActiveDeal(int observationDay, int cheaperChanceCount) {
		return new ObservationContext(Mode.NO_ACTIVE_DEAL, null, null, null, observationDay, cheaperChanceCount);
	}
}
