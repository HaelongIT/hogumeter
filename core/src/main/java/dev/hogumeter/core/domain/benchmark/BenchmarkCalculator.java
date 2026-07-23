package dev.hogumeter.core.domain.benchmark;

import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.Quantiles;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.OutlierFlag;
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

	/** 표시 손잡이 없이 조회하는 호출부 호환 seam — includeOutliers=false와 동일. */
	public BenchmarkView compute(List<DealEvent> candidates, Long currentPrice,
			int periodMonths, BenchmarkParams params, Clock clock) {
		return compute(candidates, currentPrice, periodMonths, params, clock, false);
	}

	/**
	 * @param includeOutliers 표시 손잡이(Q-11). true여도 계산 진실(n·tier·benchmarkPrice 등)은
	 *     불변 — 이상치는 항상 pricingSample에서 제외된다. 이 값은 오직 {@code outliers} 표시
	 *     목록을 채우는 데만 쓰인다.
	 */
	public BenchmarkView compute(List<DealEvent> candidates, Long currentPrice,
			int periodMonths, BenchmarkParams params, Clock clock, boolean includeOutliers) {
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

		// 8. 표시 손잡이(Q-11) — 계산 진실(위 1~7)과 완전히 분리된 사후 조립. pricingSample이 아니라
		// candidates에서 뽑는다(pricingSample은 이미 이상치를 뺀 뒤라 여기 낼 것이 없다). 손잡이가
		// 꺼져 있으면 후보를 거르지도 않는다 — "요청 안 한 목록"과 "요청했는데 0건"을 같게 두되
		// 비용은 손잡이가 켜졌을 때만 든다.
		List<BenchmarkView.DealRef> outliers = includeOutliers
				? within(candidates, now, zone, effectiveMonths).stream()
						.filter(d -> d.outlierFlag() != OutlierFlag.NONE)
						.sorted(Comparator.comparingLong(DealEvent::priceFirst))
						.map(d -> toRef(d, zone))
						.toList()
				: List.of();

		return new BenchmarkView(tier, benchmarkPrice, goodDealLine, periodLowest, latestDeal,
				n, m, expandedToMonths, currentPrice, gap, cases, outliers);
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
		return new BenchmarkView.DealRef(d.priceFirst(), toDate(d.firstSeen(), zone), d.site(), d.sourceUrl(),
				d.appliedConditions().stream().sorted().toList()); // 정렬로 결정적(표시용)
	}

	private static LocalDate toDate(Instant instant, ZoneId zone) {
		return LocalDate.ofInstant(instant, zone);
	}

	private static BenchmarkView.Gap.Leg leg(Long currentPrice, Long reference) {
		if (currentPrice == null || reference == null) {
			return null; // 현재가 미확립(Q-53) 또는 참조가 부재 — 갭을 지어내지 않는다
		}
		long won = currentPrice - reference;
		BigDecimal pct = BigDecimal.valueOf(won).multiply(HUNDRED)
				.divide(BigDecimal.valueOf(reference), 1, RoundingMode.HALF_UP);
		return new BenchmarkView.Gap.Leg(won, pct);
	}
}
