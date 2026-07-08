package dev.hogumeter.core.domain.cadence;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/16 CAD — occurrenceSet·firstSeen 축. eventCount + 간격 median(가드 n≥K_display), 경과일, 예상일 금지. */
class CadenceCalculatorTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
	private static final Instant OBSERVED_FROM = Instant.parse("2025-01-01T00:00:00Z");

	private final CadenceCalculator calculator = new CadenceCalculator();

	private static DealEvent dealAt(String date) {
		return aDealEvent().firstSeen(date + "T00:00:00Z").build();
	}

	@Test
	void computesEventCountIntervalMedianAndElapsed() {
		// 월 간격 6건: 간격 [31,28,31,30,31]일 → median 31, 경과일 07-15−06-15 = 30
		List<DealEvent> deals = List.of(
				dealAt("2026-01-15"), dealAt("2026-02-15"), dealAt("2026-03-15"),
				dealAt("2026-04-15"), dealAt("2026-05-15"), dealAt("2026-06-15"));

		CadenceView view = calculator.compute(deals, OBSERVED_FROM, 12, 5, CLOCK);

		assertThat(view.eventCount()).isEqualTo(6);
		assertThat(view.guardMet()).isTrue();
		assertThat(view.intervalMedianDays()).isEqualTo(31L);
		assertThat(view.elapsedDays()).isEqualTo(30L);
	}

	@Test
	void guardMissBelowKDisplayYieldsNoMedianButKeepsElapsed() {
		List<DealEvent> deals = List.of(dealAt("2026-01-15"), dealAt("2026-04-15"), dealAt("2026-06-15"));

		CadenceView view = calculator.compute(deals, OBSERVED_FROM, 12, 5, CLOCK);

		assertThat(view.eventCount()).isEqualTo(3);
		assertThat(view.guardMet()).isFalse();
		assertThat(view.intervalMedianDays()).isNull(); // "주기 판단 불가(발생 3건)"
		assertThat(view.elapsedDays()).isEqualTo(30L); // 경과일은 여전히
	}

	@Test
	void occurrenceSetExcludesUpperAndRejectedLowerButIncludesPendingLower() {
		List<DealEvent> deals = List.of(
				dealAt("2026-01-15"), dealAt("2026-02-15"), dealAt("2026-03-15"),
				dealAt("2026-04-15"), dealAt("2026-05-15"),
				aDealEvent().firstSeen("2026-06-15T00:00:00Z").outlier(OutlierFlag.UPPER).build(), // 제외
				aDealEvent().firstSeen("2026-06-20T00:00:00Z").outlier(OutlierFlag.LOWER)
						.permanentlyExcluded().build(), // 기각 → 제외
				aDealEvent().firstSeen("2026-06-25T00:00:00Z").outlier(OutlierFlag.LOWER).build()); // 미확정 → 포함

		CadenceView view = calculator.compute(deals, OBSERVED_FROM, 12, 5, CLOCK);

		assertThat(view.eventCount()).isEqualTo(6); // 정상 5 + 미확정 LOWER 1
	}
}
