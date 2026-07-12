package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.BenchmarkParams;
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
	private final Clock clock;
	private final BenchmarkCalculator calculator = new BenchmarkCalculator();

	public GetBenchmarkUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			CurrentPriceProvider currentPrice, Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.currentPrice = currentPrice;
		this.clock = clock;
	}

	public BenchmarkView getBenchmark(long variantId, int periodMonths, boolean includeOutliers) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = dealEvents.findByVariantId(variantId).stream()
				.map(mapper::toDomain)
				.toList();
		Long current = currentPrice.currentPriceFor(variantId); // 미확립이면 null(Q-53)
		return calculator.compute(deals, current, periodMonths, BenchmarkParams.defaults(), clock);
	}
}
