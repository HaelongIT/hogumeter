package dev.hogumeter.core.domain.signal;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.Gap;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.PricePoint;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/16 SIG — 자격=signalSet, 색=priceLast. 🟢 P25↓ / 🟡 기준가↓ / 🔴 없음 / ⚪ SPARSE·NONE. 표시 전용. */
class SignalCalculatorTest {

	private static final Instant LAST_POLL = Instant.parse("2026-07-15T00:00:00Z");
	private static final Duration FRESH = Duration.ofHours(48);
	private static final Duration QUALIFY = Duration.ofDays(7);

	private final SignalCalculator calculator = new SignalCalculator();

	private static BenchmarkView sufficient(Long benchmark, Long goodDealLine, int m) {
		return new BenchmarkView(Tier.SUFFICIENT, benchmark, goodDealLine,
				new PricePoint(820_000L, LocalDate.of(2026, 6, 1)), null, 7, m, null, 990_000L,
				new Gap(null, null), List.of());
	}

	private static BenchmarkView sparse() {
		return new BenchmarkView(Tier.SPARSE, null, null, null, null, 3, 0, null, 990_000L,
				new Gap(null, null), List.of());
	}

	private static DealEvent activeDeal(long priceLast, String lastEvidenceIso) {
		return aDealEvent().status(DealStatus.ACTIVE).withPriceFirst(priceLast)
				.withPrices(priceLast, priceLast, priceLast).lastSeen(lastEvidenceIso).build();
	}

	private SignalView compute(List<DealEvent> deals, BenchmarkView view) {
		return calculator.compute(deals, view, LAST_POLL, FRESH, QUALIFY);
	}

	@Test
	void greenWhenFreshActiveDealAtOrBelowP25() {
		SignalView s = compute(List.of(activeDeal(840_000L, "2026-07-14T00:00:00Z")), sufficient(890_000L, 850_000L, 3));
		assertThat(s.color()).isEqualTo(SignalColor.GREEN);
	}

	@Test
	void yellowWhenBelowBenchmarkButAboveP25() {
		SignalView s = compute(List.of(activeDeal(870_000L, "2026-07-14T00:00:00Z")), sufficient(890_000L, 850_000L, 3));
		assertThat(s.color()).isEqualTo(SignalColor.YELLOW);
	}

	@Test
	void redWhenNoActiveDealBelowBenchmark() {
		SignalView s = compute(List.of(activeDeal(950_000L, "2026-07-14T00:00:00Z")), sufficient(890_000L, 850_000L, 3));
		assertThat(s.color()).isEqualTo(SignalColor.RED);
	}

	@Test
	void grayWhenSparseOrNone() {
		SignalView s = compute(List.of(activeDeal(840_000L, "2026-07-14T00:00:00Z")), sparse());
		assertThat(s.color()).isEqualTo(SignalColor.GRAY);
	}

	@Test
	void mZeroCannotBeGreenAndFlagsGoodDealLineUnestablished() {
		// m=0 → goodDealLine null → 🟢 불가. 840k ≤ 기준가 890k → 🟡 + "굿딜라인 미확립"
		SignalView s = compute(List.of(activeDeal(840_000L, "2026-07-14T00:00:00Z")), sufficient(890_000L, null, 0));
		assertThat(s.color()).isEqualTo(SignalColor.YELLOW);
		assertThat(s.goodDealLineEstablished()).isFalse();
		assertThat(s.notes()).anyMatch(n -> n.contains("굿딜라인 미확립"));
	}

	@Test
	void staleBeyondQualifyLimitIsExcludedFromSignal() {
		// 14일 지난 딜 → 자격 상실 → 신호에서 제외 → 활성 딜 없음 → 🔴
		SignalView s = compute(List.of(activeDeal(840_000L, "2026-07-01T00:00:00Z")), sufficient(890_000L, 850_000L, 3));
		assertThat(s.color()).isEqualTo(SignalColor.RED);
	}

	@Test
	void weakenedFreshnessAddsAnnotation() {
		// 5일(확신 48h 초과, 자격 7일 이내) → 약화: 색은 유지, 주석 추가
		SignalView s = compute(List.of(activeDeal(840_000L, "2026-07-10T00:00:00Z")), sufficient(890_000L, 850_000L, 3));
		assertThat(s.color()).isEqualTo(SignalColor.GREEN);
		assertThat(s.notes()).anyMatch(n -> n.contains("약화"));
	}
}
