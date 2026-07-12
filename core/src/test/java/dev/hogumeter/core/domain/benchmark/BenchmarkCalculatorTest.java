package dev.hogumeter.core.domain.benchmark;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BM-06 기준가 산출(BenchmarkView) 순수 도메인 테스트 — docs/benchmark/04 AC-1~AC-6 + 잭팟 술어.
 * IO·Spring 없음, Clock 주입, 결정적.
 */
class BenchmarkCalculatorTest {

	// 기준 시각 고정: 2026-07-15. 기본 기간 6개월 → 하한 2026-01-15.
	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);
	private static final int PERIOD = 6;

	private final BenchmarkCalculator calculator = new BenchmarkCalculator();

	private static DealEvent single(long price, String dateIso) {
		return aDealEvent().withPriceFirst(price).singleSite().firstSeen(dateIso + "T00:00:00Z").build();
	}

	private static DealEvent cross(long price, String dateIso) {
		return aDealEvent().withPriceFirst(price).crossVerified().firstSeen(dateIso + "T00:00:00Z").build();
	}

	private BenchmarkView compute(List<DealEvent> deals, long currentPrice) {
		return calculator.compute(deals, currentPrice, PERIOD, BenchmarkParamsFixtures.defaultParams(), CLOCK);
	}

	// ---- AC-4 NONE ----
	@Test
	void noneWhenNoDeals() {
		BenchmarkView view = compute(List.of(), 990_000L);

		assertThat(view.tier()).isEqualTo(Tier.NONE);
		assertThat(view.benchmarkPrice()).isNull();
		assertThat(view.goodDealLine()).isNull();
		assertThat(view.periodLowest()).isNull();
		assertThat(view.latestDeal()).isNull();
		assertThat(view.n()).isZero();
		assertThat(view.m()).isZero();
		assertThat(view.expandedToMonths()).isNull();
		assertThat(view.currentPrice()).isEqualTo(990_000L);
		assertThat(view.gap().vsBenchmark()).isNull();
		assertThat(view.gap().vsLowest()).isNull();
		assertThat(view.cases()).isEmpty();
	}

	// ---- AC-3 SPARSE ----
	@Test
	void sparseForcesNullStatsAndListsCases() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"),
				single(850_000L, "2026-06-01"),
				single(900_000L, "2026-05-01"));

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.tier()).isEqualTo(Tier.SPARSE);
		assertThat(view.benchmarkPrice()).isNull(); // 통계 용어 금지 — 도메인 강제
		assertThat(view.goodDealLine()).isNull();
		assertThat(view.periodLowest()).isNull();
		assertThat(view.n()).isEqualTo(3);
		assertThat(view.cases()).extracting(BenchmarkView.DealRef::price)
				.containsExactlyInAnyOrder(800_000L, 850_000L, 900_000L);
	}

	// ---- AC-1 SUFFICIENT: median(전체 n) + 카운트 ----
	@Test
	void sufficientComputesMedianTierAndCounts() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"),
				cross(820_000L, "2026-06-20"),
				single(850_000L, "2026-06-10"),
				cross(900_000L, "2026-07-12"), // 가장 최근 → latestDeal
				single(950_000L, "2026-05-15"));

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.benchmarkPrice()).isEqualTo(850_000L); // median{800,820,850,900,950}k = 850k
		assertThat(view.n()).isEqualTo(5);
		assertThat(view.m()).isEqualTo(2);
		assertThat(view.expandedToMonths()).isNull(); // 기간 내 충분한 과거 딜 없음 → 확장 무발동
		assertThat(view.latestDeal().price()).isEqualTo(900_000L);
	}

	// ---- AC-2 단일 사이트 가중 분리: median 전체, P25·최저 교차만 ----
	@Test
	void medianOverAllButP25AndLowestUseCrossVerifiedOnly() {
		List<DealEvent> deals = List.of(
				cross(820_000L, "2026-06-10"), // 교차 최저
				cross(880_000L, "2026-06-15"),
				single(800_000L, "2026-07-01"),
				single(850_000L, "2026-06-20"),
				single(900_000L, "2026-05-20"),
				single(950_000L, "2026-05-10"));

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		// median{800,820,850,880,900,950}k = (850k+880k)/2 = 865k
		assertThat(view.benchmarkPrice()).isEqualTo(865_000L);
		// P25 교차 {820k,880k} = 835k
		assertThat(view.goodDealLine()).isEqualTo(835_000L);
		// 최저는 교차만 → 820k (단일 800k은 제외)
		assertThat(view.periodLowest().price()).isEqualTo(820_000L);
		assertThat(view.periodLowest().date()).isEqualTo(LocalDate.of(2026, 6, 10));
		assertThat(view.n()).isEqualTo(6);
		assertThat(view.m()).isEqualTo(2);
	}

	// ---- AC-7 절: 이상치는 tier·median 이전에 제외 ----
	@Test
	void excludesOutliersBeforeTierAndMedian() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"),
				single(820_000L, "2026-06-20"),
				single(850_000L, "2026-06-10"),
				single(900_000L, "2026-06-05"),
				single(950_000L, "2026-05-15"),
				aDealEvent().withPriceFirst(5_000_000L).singleSite().outlier(OutlierFlag.UPPER)
						.firstSeen("2026-07-02T00:00:00Z").build(),
				aDealEvent().withPriceFirst(10_000L).singleSite().outlier(OutlierFlag.LOWER)
						.firstSeen("2026-07-03T00:00:00Z").build());

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.n()).isEqualTo(5); // 이상치 2건 제외
		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.benchmarkPrice()).isEqualTo(850_000L); // 이상치 미포함 median
	}

	// ---- BM-05 AC-3 연동: 영구 제외(사기 기각) 딜은 outlierFlag NONE이어도 표본 배제 ----
	@Test
	void excludesPermanentlyRejectedDealsFromSample() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"), single(820_000L, "2026-06-20"),
				single(850_000L, "2026-06-10"), single(900_000L, "2026-06-05"),
				single(950_000L, "2026-05-15"),
				aDealEvent().withPriceFirst(10_000L).singleSite().permanentlyExcluded()
						.firstSeen("2026-07-02T00:00:00Z").build());

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.n()).isEqualTo(5);
		assertThat(view.benchmarkPrice()).isEqualTo(850_000L);
	}

	// ---- AC-5/BM-04 AC-5: BACKFILL 단일사이트는 n엔 포함, m·P25·최저엔 미포함 ----
	@Test
	void backfillCountsInNButNotInCrossVerifiedStats() {
		List<DealEvent> deals = List.of(
				cross(820_000L, "2026-06-10"),
				cross(850_000L, "2026-06-12"),
				cross(880_000L, "2026-06-14"),
				cross(900_000L, "2026-06-16"),
				aDealEvent().withPriceFirst(800_000L).singleSite().origin(Origin.BACKFILL)
						.firstSeen("2026-06-01T00:00:00Z").build());

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.n()).isEqualTo(5); // 백필 포함
		assertThat(view.m()).isEqualTo(4); // 교차만
		// median 전체 5 {800,820,850,880,900}k = 850k
		assertThat(view.benchmarkPrice()).isEqualTo(850_000L);
		// 최저는 교차만 → 820k (백필 800k 제외)
		assertThat(view.periodLowest().price()).isEqualTo(820_000L);
		// P25 교차 4 {820,850,880,900}k = 842.5k → 842,500
		assertThat(view.goodDealLine()).isEqualTo(842_500L);
	}

	// ---- AC-5 자동확장: kFill까지 확장 발동 ----
	@Test
	void autoExpandsPeriodUntilKFillReached() {
		// 기간 1개월엔 3건뿐, 과거로 확장하면 총 7건(=kFill)
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"),
				single(810_000L, "2026-06-25"),
				single(820_000L, "2026-06-20"),
				single(830_000L, "2026-04-01"),
				single(840_000L, "2026-03-01"),
				single(850_000L, "2026-02-01"),
				single(860_000L, "2026-01-01"));

		BenchmarkView view = calculator.compute(deals, 990_000L, 1,
				BenchmarkParamsFixtures.defaultParams(), CLOCK);

		assertThat(view.n()).isEqualTo(7);
		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.expandedToMonths()).isEqualTo(7);
	}

	// ---- AC-5 상한: 12개월 밖 딜은 확장해도 미포함 ----
	@Test
	void doesNotPullDealsBeyondTwelveMonthCap() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"), // 기간 내 1건
				single(810_000L, "2025-01-01"), // >12개월 → 상한 밖
				single(820_000L, "2024-12-01"),
				single(830_000L, "2024-11-01"));

		BenchmarkView view = calculator.compute(deals, 990_000L, 1,
				BenchmarkParamsFixtures.defaultParams(), CLOCK);

		assertThat(view.n()).isEqualTo(1); // 상한 밖 3건 제외
		assertThat(view.tier()).isEqualTo(Tier.SPARSE);
		assertThat(view.expandedToMonths()).isNull(); // 확장해도 새 딜 없음
	}

	// ---- AC-5: 기간 내 이미 kFill 충족 → 확장 무발동 ----
	@Test
	void noExpansionWhenPeriodAlreadyHasKFill() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"), single(810_000L, "2026-06-25"),
				single(820_000L, "2026-06-20"), single(830_000L, "2026-06-10"),
				single(840_000L, "2026-05-20"), single(850_000L, "2026-05-01"),
				single(860_000L, "2026-04-10"));

		BenchmarkView view = compute(deals, 990_000L); // 기간 6개월

		assertThat(view.n()).isEqualTo(7);
		assertThat(view.expandedToMonths()).isNull();
	}

	// ---- AC-5: 기간 내 kFill 미달이나 과거 딜도 없음 → 확장 무발동(no-op) ----
	@Test
	void noExpansionWhenNoOlderDealsExist() {
		List<DealEvent> deals = List.of(
				single(800_000L, "2026-07-01"), single(820_000L, "2026-06-20"),
				single(850_000L, "2026-06-10"), single(900_000L, "2026-05-20"),
				single(950_000L, "2026-05-10"));

		BenchmarkView view = compute(deals, 990_000L); // n=5 < kFill=7, but no older deals

		assertThat(view.n()).isEqualTo(5);
		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.expandedToMonths()).isNull();
	}

	// ---- AC-6 갭 계산: 원·% 병기 ----
	@Test
	void gapVsBenchmarkAndVsLowest() {
		List<DealEvent> deals = List.of(
				cross(820_000L, "2026-06-10"), cross(850_000L, "2026-06-12"),
				cross(890_000L, "2026-06-14"), cross(920_000L, "2026-06-16"),
				cross(950_000L, "2026-06-18"));

		BenchmarkView view = compute(deals, 990_000L);

		assertThat(view.benchmarkPrice()).isEqualTo(890_000L);
		assertThat(view.periodLowest().price()).isEqualTo(820_000L);
		assertThat(view.gap().vsBenchmark().won()).isEqualTo(100_000L);
		assertThat(view.gap().vsBenchmark().pct()).isEqualByComparingTo(new BigDecimal("11.2"));
		assertThat(view.gap().vsLowest().won()).isEqualTo(170_000L);
		assertThat(view.gap().vsLowest().pct()).isEqualByComparingTo(new BigDecimal("20.7"));
	}

	// ---- Q-53: 현재가 미확립(null)이면 갭을 지어내지 않는다 ----
	@Test
	void currentPriceUnavailableYieldsNullGapNotMinusHundredPercent() {
		// 갭이 실제로 계산되던 SUFFICIENT 표본. 현재가만 미확립(null)이면 두 leg는 null이어야 한다 —
		// 0을 넣었다면 vsBenchmark = 0−890,000 = −100%("지금 100% 싸다")라는 거짓 신호가 됐다.
		List<DealEvent> deals = List.of(
				cross(820_000L, "2026-06-10"), cross(850_000L, "2026-06-12"),
				cross(890_000L, "2026-06-14"), cross(920_000L, "2026-06-16"),
				cross(950_000L, "2026-06-18"));

		BenchmarkView view = calculator.compute(deals, null, PERIOD, BenchmarkParamsFixtures.defaultParams(), CLOCK);

		assertThat(view.currentPrice()).isNull();
		assertThat(view.gap().vsBenchmark()).isNull();
		assertThat(view.gap().vsLowest()).isNull();
		// 현재가와 무관한 통계는 그대로 산출된다 — 갭만 미확립이다.
		assertThat(view.benchmarkPrice()).isEqualTo(890_000L);
		assertThat(view.periodLowest().price()).isEqualTo(820_000L);
	}

	// ---- AC-4 대박딜 폴백 경계(30%) ----
	@ParameterizedTest
	@CsvSource({
			"700000, true",  // 정확히 30% 싸다 → 포함
			"699999, true",  // 30% 초과 싸다
			"700001, false", // 30% 미만
			"800000, false"  // 20%만 싸다
	})
	void coldStartJackpotBoundaryAtThirtyPercent(long dealPrice, boolean expected) {
		boolean qualifies = calculator.qualifiesAsColdStartJackpot(
				dealPrice, 1_000_000L, BenchmarkParamsFixtures.defaultParams());

		assertThat(qualifies).isEqualTo(expected);
	}

	// ---- 무효 기간 방어(BM_INVALID_PERIOD seam) ----
	@Test
	void rejectsNonPositivePeriod() {
		assertThatThrownBy(() -> calculator.compute(List.of(), 990_000L, 0,
				BenchmarkParamsFixtures.defaultParams(), CLOCK))
				.isInstanceOf(InvalidBenchmarkPeriodException.class);
		assertThatThrownBy(() -> calculator.compute(List.of(), 990_000L, -1,
				BenchmarkParamsFixtures.defaultParams(), CLOCK))
				.isInstanceOf(InvalidBenchmarkPeriodException.class);
	}
}
