package dev.hogumeter.core.domain.benchmark;

import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.Quantiles;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * BM-06 기준가 산출 — 순수 함수(IO·상태·랜덤 없음, Clock 주입). DealEvent 목록을 받아 BenchmarkView를 재계산.
 * 파이프라인: 이상치 선제외 → 기간 윈도우+자동확장 → 3단 tier 판정 → tier별 통계 → 갭.
 * 수치는 전부 주입받은 {@link BenchmarkParams}에서 읽는다(하드코딩 금지).
 */
public class BenchmarkCalculator {

	private static final BigDecimal HUNDRED = new BigDecimal("100");
	private static final BigDecimal ONE = BigDecimal.ONE;

	public BenchmarkView compute(List<DealEvent> candidates, long currentPrice,
			int periodMonths, BenchmarkParams params, Clock clock) {
		if (periodMonths <= 0) {
			throw new InvalidBenchmarkPeriodException(periodMonths);
		}
		Instant now = clock.instant();
		ZoneId zone = clock.getZone();

		// 2. pricingSet 선필터 (docs/03 3-1: 값 통계 표본 = 이상치·영구제외·미상 제외). tier·median보다 항상 먼저.
		List<DealEvent> pricingSample = DealSets.pricingSet(candidates);

		// 3. 기간 윈도우 + K_FILL 자동확장 (과거 딜이 실제로 추가될 때만 확장 표기)
		List<DealEvent> sample = within(pricingSample, now, zone, periodMonths);
		int effectiveMonths = periodMonths;
		if (sample.size() < params.kFill()) {
			for (int months = periodMonths + 1; months <= params.expandLimitMonths(); months++) {
				List<DealEvent> wider = within(pricingSample, now, zone, months);
				if (wider.size() > sample.size()) {
					sample = wider;
					effectiveMonths = months;
					if (sample.size() >= params.kFill()) {
						break;
					}
				}
			}
		}
		Integer expandedToMonths = (effectiveMonths > periodMonths) ? effectiveMonths : null;

		// 4. 카운트: n=전체 유효, m=교차검증만
		int n = sample.size();
		List<DealEvent> crossVerified = sample.stream().filter(DealEvent::crossVerified).toList();
		int m = crossVerified.size();

		// 5. 3단 tier
		Tier tier = tierOf(n, params.kDisplay());

		DealEvent latest = sample.stream().max(Comparator.comparing(DealEvent::firstSeen)).orElse(null);
		BenchmarkView.DealRef latestDeal = (latest == null) ? null : toRef(latest, zone);

		// 6. tier별 필드
		Long benchmarkPrice = null;
		Long goodDealLine = null;
		BenchmarkView.PricePoint periodLowest = null;
		List<BenchmarkView.DealRef> cases = List.of();

		if (tier == Tier.SUFFICIENT) {
			benchmarkPrice = Quantiles.medianWon(pricesOf(sample)); // 전체 n의 median(균등)
			if (!crossVerified.isEmpty()) {
				goodDealLine = Quantiles.percentileWon(pricesOf(crossVerified), Quantiles.P25);
				DealEvent lowest = crossVerified.stream()
						.min(Comparator.comparingLong(DealEvent::priceFirst)).orElseThrow();
				periodLowest = new BenchmarkView.PricePoint(lowest.priceFirst(), toDate(lowest.firstSeen(), zone));
			}
		} else if (tier == Tier.SPARSE) {
			cases = sample.stream().map(d -> toRef(d, zone)).toList(); // 사례 리스트만
		}

		// 7. 갭(원·%)
		BenchmarkView.Gap gap = new BenchmarkView.Gap(
				leg(currentPrice, benchmarkPrice),
				leg(currentPrice, periodLowest == null ? null : periodLowest.price()));

		return new BenchmarkView(tier, benchmarkPrice, goodDealLine, periodLowest, latestDeal,
				n, m, expandedToMonths, currentPrice, gap, cases);
	}

	/**
	 * NONE(n=0) 구간의 유일 알림 트리거(BM-06 AC-4): 현재가 대비 coldStartJackpotRatio 이상 싼 딜.
	 * dealPriceFirst ≤ currentPrice·(1−ratio) 이면 "기준 미확립·참고용" 대상.
	 */
	public boolean qualifiesAsColdStartJackpot(long dealPriceFirst, long currentPrice, BenchmarkParams params) {
		BigDecimal threshold = BigDecimal.valueOf(currentPrice)
				.multiply(ONE.subtract(params.coldStartJackpotRatio()));
		return BigDecimal.valueOf(dealPriceFirst).compareTo(threshold) <= 0;
	}

	private static Tier tierOf(int n, int kDisplay) {
		if (n == 0) {
			return Tier.NONE;
		}
		return (n >= kDisplay) ? Tier.SUFFICIENT : Tier.SPARSE;
	}

	/** firstSeen ∈ [now − months, now] 인 딜만. 월 연산은 Clock의 zone 기준. */
	private static List<DealEvent> within(List<DealEvent> deals, Instant now, ZoneId zone, int months) {
		Instant lower = ZonedDateTime.ofInstant(now, zone).minusMonths(months).toInstant();
		return deals.stream()
				.filter(d -> !d.firstSeen().isAfter(now))
				.filter(d -> !d.firstSeen().isBefore(lower))
				.toList();
	}

	private static List<Long> pricesOf(List<DealEvent> deals) {
		return deals.stream().map(DealEvent::priceFirst).toList();
	}

	private static BenchmarkView.DealRef toRef(DealEvent d, ZoneId zone) {
		return new BenchmarkView.DealRef(d.priceFirst(), toDate(d.firstSeen(), zone), d.site(), d.sourceUrl());
	}

	private static LocalDate toDate(Instant instant, ZoneId zone) {
		return LocalDate.ofInstant(instant, zone);
	}

	private static BenchmarkView.Gap.Leg leg(long currentPrice, Long reference) {
		if (reference == null) {
			return null;
		}
		long won = currentPrice - reference;
		BigDecimal pct = BigDecimal.valueOf(won).multiply(HUNDRED)
				.divide(BigDecimal.valueOf(reference), 1, RoundingMode.HALF_UP);
		return new BenchmarkView.Gap.Leg(won, pct);
	}
}
