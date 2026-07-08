package dev.hogumeter.core.domain.purchase;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * PUR-05 관찰 문맥 산출(순수 함수). REPORT_PENDING이면 집계중, 활성 딜(signalSet)이 있으면 최저 priceLast로
 * 상회분, 없으면 관찰 D일차 + pricingSet의 "내 구매가보다 싼 기회"(priceMin &lt; paidPrice) 건수.
 */
public class ObservationContextCalculator {

	public ObservationContext compute(Purchase purchase, List<DealEvent> deals, Instant now) {
		if (purchase.state() == PurchaseState.REPORT_PENDING) {
			return ObservationContext.pending();
		}
		List<DealEvent> active = DealSets.signalSet(deals);
		if (!active.isEmpty()) {
			long lowest = active.stream().mapToLong(DealEvent::priceLast).min().getAsLong();
			long overpaid = purchase.paidPrice() - lowest;
			BigDecimal pct = BigDecimal.valueOf(overpaid)
					.divide(BigDecimal.valueOf(purchase.paidPrice()), 3, RoundingMode.HALF_UP);
			return ObservationContext.activeDeal(lowest, overpaid, pct);
		}
		int observationDay = (int) Duration.between(purchase.purchasedAt(), now).toDays() + 1; // 1-base
		int cheaperChances = (int) DealSets.pricingSet(deals).stream()
				.filter(d -> d.priceMin() < purchase.paidPrice())
				.count();
		return ObservationContext.noActiveDeal(observationDay, cheaperChances);
	}
}
