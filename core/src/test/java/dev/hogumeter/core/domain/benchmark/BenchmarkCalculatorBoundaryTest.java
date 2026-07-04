package dev.hogumeter.core.domain.benchmark;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.deal.DealEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BM-06 AC-7 경계값 계약(파라미터라이즈드 필수): n = 0/1/4/5/7 × K_DISPLAY = 3/5/10.
 * tier 경계는 K_DISPLAY, K_FILL 불변식(&gt; K_DISPLAY) 보존. 모든 딜을 기간 내에 배치해 확장을 무발동으로 격리.
 * tier 규칙: n==0 → NONE / n&gt;=K_DISPLAY → SUFFICIENT / 그 외 → SPARSE.
 */
class BenchmarkCalculatorBoundaryTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
	private static final int PERIOD = 6;

	private final BenchmarkCalculator calculator = new BenchmarkCalculator();

	@ParameterizedTest(name = "n={0}, K_DISPLAY={1} → {2}")
	@CsvSource({
			"0, 3, NONE", "0, 5, NONE", "0, 10, NONE",
			"1, 3, SPARSE", "1, 5, SPARSE", "1, 10, SPARSE",
			"4, 3, SUFFICIENT", "4, 5, SPARSE", "4, 10, SPARSE",
			"5, 3, SUFFICIENT", "5, 5, SUFFICIENT", "5, 10, SPARSE",
			"7, 3, SUFFICIENT", "7, 5, SUFFICIENT", "7, 10, SPARSE"
	})
	void tierBoundaryAcrossNAndKDisplay(int n, int kDisplay, Tier expectedTier) {
		BenchmarkParams params = BenchmarkParamsFixtures.params(kDisplay);
		List<DealEvent> deals = nSingleSiteDealsWithinPeriod(n);

		BenchmarkView view = calculator.compute(deals, 990_000L, PERIOD, params, CLOCK);

		assertThat(view.tier()).isEqualTo(expectedTier);
		assertThat(view.n()).isEqualTo(n);
		assertThat(view.m()).isZero(); // 전부 단일 사이트
		assertThat(view.expandedToMonths()).isNull(); // 기간 내 배치 → 확장 무발동
		assertThat(params.kFill()).isGreaterThan(kDisplay); // 불변식 재확인
	}

	private static List<DealEvent> nSingleSiteDealsWithinPeriod(int n) {
		List<DealEvent> deals = new ArrayList<>();
		LocalDate base = LocalDate.of(2026, 7, 1);
		for (int i = 0; i < n; i++) {
			Instant firstSeen = base.minusDays(i).atStartOfDay(ZoneOffset.UTC).toInstant();
			deals.add(aDealEvent().withPriceFirst(800_000L + i * 10_000L).singleSite().firstSeen(firstSeen).build());
		}
		return deals;
	}
}
