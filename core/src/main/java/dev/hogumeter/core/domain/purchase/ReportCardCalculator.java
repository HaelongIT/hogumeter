package dev.hogumeter.core.domain.purchase;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * PUR-04 성적표 산출 — 순수 함수. 산입 = pricingSet ∩ 관찰기간 ∩ [observedFrom, ..]. percentile Y=X/n(동가 미포함),
 * "기간 내 최저 기회" = min priceMin. purchasedAt이 observedFrom 이전이면 UNOBSERVED(통계 없음).
 * capturedAt ≤ 발급(지각 백필 제외)은 DealEvent에 capturedAt 부재로 미적용 — 후속(docs/91 Q-32).
 */
public class ReportCardCalculator {

	public ReportCard compute(List<DealEvent> deals, long paidPrice, Instant observationStart,
			Instant observationEnd, Instant observedFrom, Long benchmarkPrice) {
		Long paidGap = benchmarkPrice != null ? paidPrice - benchmarkPrice : null;

		if (observationStart.isBefore(observedFrom)) {
			return new ReportCard(true, 0, 0, null, null, paidPrice, paidGap); // UNOBSERVED
		}

		List<DealEvent> inScope = DealSets.pricingSet(deals).stream()
				.filter(d -> !d.firstSeen().isBefore(observationStart) && !d.firstSeen().isAfter(observationEnd))
				.filter(d -> !d.firstSeen().isBefore(observedFrom))
				.toList();
		int n = inScope.size();
		if (n == 0) {
			return new ReportCard(false, 0, 0, null, null, paidPrice, paidGap);
		}

		int cheaperCount = (int) inScope.stream().filter(d -> d.priceFirst() < paidPrice).count();
		BigDecimal percentile = BigDecimal.valueOf(cheaperCount)
				.divide(BigDecimal.valueOf(n), 3, RoundingMode.HALF_UP);
		long lowestOpportunity = inScope.stream().mapToLong(DealEvent::priceMin).min().orElseThrow();

		return new ReportCard(false, n, cheaperCount, percentile, lowestOpportunity, paidPrice, paidGap);
	}
}
