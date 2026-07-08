package dev.hogumeter.core.domain.purchase;

import java.math.BigDecimal;

/**
 * PUR-04 성적표 — "호구였나"의 최종 판정. 성분별 잣대 분리(percentile=priceFirst, 최저 기회=priceMin).
 * 유머 등급 라벨 없음(정직성). UNOBSERVED면 통계 필드 null.
 *
 * @param cheaperCount 내 구매가보다 싼 딜 수 X(동가 미포함)
 * @param percentile Y = X/n(0~1). n=0 또는 UNOBSERVED면 null
 * @param lowestOpportunity 기간 내 최저 기회(min priceMin). null 가능
 * @param paidGap 구매가 − 기준가(있으면). 아래면 음수
 */
public record ReportCard(boolean unobserved, int n, int cheaperCount, BigDecimal percentile,
		Long lowestOpportunity, long paidPrice, Long paidGap) {
}
