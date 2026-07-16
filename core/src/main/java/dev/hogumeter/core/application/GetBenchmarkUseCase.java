package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.benchmark.BenchmarkCalculator;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 기준가 조회 유스케이스(BM-06 배선). variant의 deal_event를 로드·도메인 매핑해 순수 계산기에 넘긴다.
 * includeOutliers는 표시 손잡이라 계산 진실 불변(이상치 항상 제외) — 표시용 목록 배선은 후속(docs/91 Q-11).
 */
@Service
public class GetBenchmarkUseCase {

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final CurrentPriceProvider currentPrice;
	private final VariantBenchmarkParams params;
	private final VariantDemandScope demandScope;
	private final Clock clock;
	private final BenchmarkCalculator calculator = new BenchmarkCalculator();

	public GetBenchmarkUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			CurrentPriceProvider currentPrice, VariantBenchmarkParams params, VariantDemandScope demandScope,
			Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.currentPrice = currentPrice;
		this.params = params;
		this.demandScope = demandScope;
		this.clock = clock;
	}

	public BenchmarkView getBenchmark(long variantId, int periodMonths, boolean includeOutliers) {
		return getBenchmark(variantId, periodMonths, includeOutliers, null);
	}

	/**
	 * @param demandAxisValue 분리(SPLIT) 제품에서 볼 수요축 값(Q-66 ①). 묶음이면 무시하고, 분리인데
	 *     없으면 거절한다 — 전체 딜로 답하면 그게 묶음의 거짓말이다.
	 */
	public BenchmarkView getBenchmark(long variantId, int periodMonths, boolean includeOutliers,
			String demandAxisValue) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = dealEvents.findByVariantId(variantId).stream()
				.map(mapper::toDomain)
				.toList();
		// 분리 제품이면 그 축 값의 딜만 본다(값 미상 딜은 자동으로 빠진다 — 확정본 §41).
		deals = demandScope.scope(variantId, deals, demandAxisValue);
		Long current = currentPrice.currentPriceFor(variantId); // 미확립이면 null(Q-53)
		// K는 사용자 손잡이라 variant마다 다르다(Q-48 ①) — 나머지 수치는 시스템 고정.
		return calculator.compute(deals, current, periodMonths, params.of(variantId), clock);
	}
}
