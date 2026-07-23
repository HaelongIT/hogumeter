package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.used.EvaluationInput;
import dev.hogumeter.core.domain.used.EvaluationKind;
import dev.hogumeter.core.domain.used.ExtractedListing;
import dev.hogumeter.core.domain.used.ListingExtractor;
import dev.hogumeter.core.domain.used.ListingStatus;
import dev.hogumeter.core.domain.used.PriceContext;
import dev.hogumeter.core.domain.used.PriceContextCalculator;
import dev.hogumeter.core.domain.used.RiskSignal;
import dev.hogumeter.core.domain.used.UsedRiskSignals;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * USED-04 평가기 배선(AC-12·13). 입력 3단(URL→TEXT→MANUAL)을 한 입구로 받아 구조화하고,
 * 가격 맥락·위험 신호를 붙여 돌려준다. 이 유스케이스가 생기기 전 {@code ListingExtractor}·
 * {@code UsedRiskSignals}·{@code PriceContextCalculator}는 프로덕션 호출자가 0이었다.
 *
 * <p><b>URL은 실 fetch하지 않는다</b>(정지조건). 대신 이미 폴링해 아는 매물인지(V13
 * {@code listing.url})만 본다 — 못 찾으면 "TEXT를 요청"으로 폴백한다(docs/91 Q-76).
 *
 * <p>위험 신호의 "업자 레퍼토리 키워드"는 아직 전용 어휘가 없어 이 조건검색의
 * {@code exclude_keywords}를 재사용한다(docs/91 Q-77) — 사용자가 이미 입력한 값이라
 * 새 어휘를 지어내지 않는다. cheap-threshold(30%)는 잠정 상수(같은 Q).
 */
@Service
public class EvaluateListingUseCase {

	private static final int CHEAP_THRESHOLD_PCT = 30; // Q-77 잠정
	private static final int BENCHMARK_PERIOD_MONTHS = 6;

	private final UsedSearchRepository searches;
	private final ListingRepository listings;
	private final ListingExtractor extractor;
	private final GetBenchmarkUseCase benchmark;

	public EvaluateListingUseCase(UsedSearchRepository searches, ListingRepository listings,
			ListingExtractor extractor, GetBenchmarkUseCase benchmark) {
		this.searches = searches;
		this.listings = listings;
		this.extractor = extractor;
		this.benchmark = benchmark;
	}

	/**
	 * @param variantIdForBenchmark 신품 기준가 대비 %를 낼 variant. null이면 그 필드는 null —
	 *     안 주면 "비교 안 함"이지 "0% 차이"가 아니다(과대약속 금지).
	 */
	public EvaluationOutcome evaluate(long usedSearchId, EvaluationInput input, Long variantIdForBenchmark) {
		UsedSearchEntity search = searches.findById(usedSearchId)
				.orElseThrow(() -> new UsedSearchNotFoundException(usedSearchId));

		Optional<ExtractedListing> extracted = (input.kind() == EvaluationKind.URL)
				? findAlreadyPolled(usedSearchId, input.url())
				: extractor.extract(input);

		if (extracted.isEmpty()) {
			return EvaluationOutcome.needsInput(nextFallback(input.kind()));
		}

		ExtractedListing listing = extracted.get();
		List<Long> activeSnapshot = listings.findByUsedSearchId(usedSearchId).stream()
				.filter(l -> l.getStatus() == ListingStatus.ACTIVE)
				.map(ListingEntity::getPrice)
				.toList();
		Long snapshotLowest = activeSnapshot.stream().min(Long::compareTo).orElse(null);
		Long benchmarkPrice = benchmarkPriceFor(variantIdForBenchmark);

		PriceContext priceContext = PriceContextCalculator.compute(listing.price(), activeSnapshot,
				benchmarkPrice, sourceLabel(search.getPlatform()));
		List<RiskSignal> riskSignals = UsedRiskSignals.detect(listing.title(), search.getExcludeKeywords(),
				listing.price(), snapshotLowest, CHEAP_THRESHOLD_PCT);

		return EvaluationOutcome.resolved(listing, priceContext, riskSignals);
	}

	/** URL 평가는 실 fetch 대신 이미 폴링해 아는 매물인지만 본다(정지조건 우회 아님 — 새 네트워크 호출 0). */
	private Optional<ExtractedListing> findAlreadyPolled(long usedSearchId, String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		return listings.findByUsedSearchId(usedSearchId).stream()
				.filter(l -> url.equals(l.getUrl()))
				.findFirst()
				.map(l -> new ExtractedListing(l.getTitle(), l.getPrice(), l.getUrl()));
	}

	private static EvaluationKind nextFallback(EvaluationKind tried) {
		return switch (tried) {
			case URL -> EvaluationKind.TEXT;
			case TEXT, MANUAL -> EvaluationKind.MANUAL;
		};
	}

	private Long benchmarkPriceFor(Long variantId) {
		if (variantId == null) {
			return null;
		}
		BenchmarkView view = benchmark.getBenchmark(variantId, BENCHMARK_PERIOD_MONTHS, false);
		return view.benchmarkPrice();
	}

	private static String sourceLabel(String platform) {
		return switch (platform) {
			case "BUNJANG" -> "번개장터 활성 매물";
			default -> platform + " 활성 매물";
		};
	}
}
