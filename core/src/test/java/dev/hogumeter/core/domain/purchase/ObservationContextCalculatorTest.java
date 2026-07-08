package dev.hogumeter.core.domain.purchase;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.purchase.ObservationContext.Mode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PUR-05 관찰 문맥 — Purchase별 1줄. 딜있음(활성 최저 priceLast) / 딜없음(D일차·싼 기회) / REPORT_PENDING. */
class ObservationContextCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-07-05T00:00:00Z");

	private final ObservationContextCalculator calculator = new ObservationContextCalculator();

	private Purchase observing(long paidPrice) {
		return Purchase.observing(1L, "256GB", paidPrice, Instant.parse("2026-07-01T00:00:00Z"), 90);
	}

	private DealEvent active(long priceLast) {
		return aDealEvent().status(DealStatus.ACTIVE).withPrices(priceLast, priceLast, priceLast).build();
	}

	private DealEvent ended(long priceMin) {
		return aDealEvent().status(DealStatus.ENDED).withPrices(priceMin, priceMin, priceMin).build();
	}

	@Test
	void reportPendingShowsAggregatingMode() {
		Purchase pending = observing(900_000L).expire(); // OBSERVING → REPORT_PENDING

		ObservationContext ctx = calculator.compute(pending, List.of(active(850_000L)), NOW);

		assertThat(ctx.mode()).isEqualTo(Mode.REPORT_PENDING);
	}

	@Test
	void activeDealsShowLowestPriceLastAndOverpaidGap() {
		ObservationContext ctx = calculator.compute(observing(950_000L),
				List.of(active(900_000L), active(850_000L)), NOW);

		assertThat(ctx.mode()).isEqualTo(Mode.ACTIVE_DEAL);
		assertThat(ctx.activeLowestPriceLast()).isEqualTo(850_000L); // 활성 딜 최저 priceLast
		assertThat(ctx.overpaidWon()).isEqualTo(100_000L); // 950k 지불 − 850k = 상회 +100k
		assertThat(ctx.overpaidPct()).isEqualTo(new BigDecimal("0.105"));
	}

	@Test
	void activeDealAbovePaidYieldsNegativeOverpaid() {
		ObservationContext ctx = calculator.compute(observing(900_000L), List.of(active(1_000_000L)), NOW);

		assertThat(ctx.mode()).isEqualTo(Mode.ACTIVE_DEAL);
		assertThat(ctx.overpaidWon()).isEqualTo(-100_000L); // 내가 더 싸게 삼
	}

	@Test
	void noActiveDealFallsBackToObservationDayAndCheaperChances() {
		// ENDED 딜은 signalSet 제외(활성 아님)이나 pricingSet엔 포함 → 싼 기회 집계 대상
		ObservationContext ctx = calculator.compute(observing(850_000L),
				List.of(ended(800_000L), ended(900_000L)), NOW);

		assertThat(ctx.mode()).isEqualTo(Mode.NO_ACTIVE_DEAL);
		assertThat(ctx.observationDay()).isEqualTo(5); // 2026-07-01 구매 → 07-05은 5일차(1-base)
		assertThat(ctx.cheaperChanceCount()).isEqualTo(1); // priceMin 800k < 850k만
	}
}
