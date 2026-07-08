package dev.hogumeter.core.domain.purchase;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PUR-04 성적표 — percentile Y=X/n(동가 미포함, pricingSet), "기간 내 최저 기회"=priceMin, UNOBSERVED. */
class ReportCardCalculatorTest {

	private static final Instant OBSERVED_FROM = Instant.parse("2025-01-01T00:00:00Z");
	private static final Instant OBS_START = Instant.parse("2026-04-01T00:00:00Z");
	private static final Instant OBS_END = Instant.parse("2026-06-30T00:00:00Z");

	private final ReportCardCalculator calculator = new ReportCardCalculator();

	private static DealEvent dealAt(long price, String date) {
		return aDealEvent().withPriceFirst(price).firstSeen(date + "T00:00:00Z").build();
	}

	@Test
	void percentileIsCheaperCountOverN() {
		// 관찰기간 내 5건 {800,820,850,880,900}k, paidPrice 850k → 더 싼 건 {800,820}=2, 동가(850) 미포함
		List<DealEvent> deals = List.of(
				dealAt(800_000, "2026-04-10"), dealAt(820_000, "2026-04-20"), dealAt(850_000, "2026-05-01"),
				dealAt(880_000, "2026-05-15"), dealAt(900_000, "2026-06-01"));

		ReportCard card = calculator.compute(deals, 850_000L, OBS_START, OBS_END, OBSERVED_FROM, 860_000L);

		assertThat(card.unobserved()).isFalse();
		assertThat(card.n()).isEqualTo(5);
		assertThat(card.cheaperCount()).isEqualTo(2); // 동가 미포함
		assertThat(card.percentile()).isEqualByComparingTo(new BigDecimal("0.400")); // 2/5
		assertThat(card.lowestOpportunity()).isEqualTo(800_000L); // 기간 내 최저 기회(min priceMin)
		assertThat(card.paidGap()).isEqualTo(-10_000L); // 850k − 기준가 860k (기준가 아래 구매)
	}

	@Test
	void excludesDealsOutsideObservationWindow() {
		List<DealEvent> deals = List.of(
				dealAt(800_000, "2026-03-01"), // 관찰 시작 이전 → 제외
				dealAt(820_000, "2026-05-01"),
				dealAt(880_000, "2026-07-15")); // 관찰 종료 이후 → 제외

		ReportCard card = calculator.compute(deals, 850_000L, OBS_START, OBS_END, OBSERVED_FROM, 860_000L);

		assertThat(card.n()).isEqualTo(1); // 관찰기간 내 1건만
		assertThat(card.lowestOpportunity()).isEqualTo(820_000L);
	}

	@Test
	void unobservedWhenPurchasedBeforeObservationStart() {
		// purchasedAt(관찰 시작)이 observedFrom 이전 → 통계 없음
		Instant beforeObserved = Instant.parse("2024-06-01T00:00:00Z");
		List<DealEvent> deals = List.of(dealAt(800_000, "2026-05-01"));

		ReportCard card = calculator.compute(deals, 850_000L, beforeObserved, OBS_END, OBSERVED_FROM, 860_000L);

		assertThat(card.unobserved()).isTrue();
		assertThat(card.percentile()).isNull();
	}
}
