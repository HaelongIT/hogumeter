package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.benchmark.BenchmarkCalculator;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.signal.SignalCalculator;
import dev.hogumeter.core.domain.signal.SignalView;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * SIG 신호등 조회(배선). 저장된 deal_event로 기준가를 재산출한 뒤 신호 색을 판정한다(compute-on-demand).
 * lastPoll(실 폴링 시각 미저장)·신선도 상수는 잠정 seam(docs/91 Q-24·Q-25·Q-26).
 */
@Service
public class GetSignalUseCase {

	private static final int PERIOD_MONTHS = 6; // Q-26 잠정
	private static final Duration FRESHNESS_LIMIT = Duration.ofHours(48); // Q-24 잠정
	private static final Duration QUALIFY_LIMIT = Duration.ofDays(7); // Q-25 잠정

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final CurrentPriceProvider currentPrice;
	private final VariantBenchmarkParams params;
	private final VariantDemandScope demandScope;
	private final VariantExcludeKeywords excludeKeywords;
	private final Clock clock;
	private final BenchmarkCalculator benchmark = new BenchmarkCalculator();
	private final SignalCalculator signal = new SignalCalculator();

	public GetSignalUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			CurrentPriceProvider currentPrice, VariantBenchmarkParams params, VariantDemandScope demandScope,
			VariantExcludeKeywords excludeKeywords, Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.currentPrice = currentPrice;
		this.params = params;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.clock = clock;
	}

	public SignalView getSignal(long variantId) {
		return getSignal(variantId, null);
	}

	/**
	 * @param demandAxisValue 분리 제품에서 볼 수요축 값(Q-66 ①). <b>기준가와 같은 표본을 봐야 한다</b> —
	 *     한쪽만 색을 가르면 같은 화면이 서로 다른 사실을 말한다("기준가는 블랙인데 신호등은 전체").
	 */
	public SignalView getSignal(long variantId, String demandAxisValue) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = excludeKeywords.filter(variantId, dealEvents.findByVariantId(variantId)).stream()
				.map(mapper::toDomain).toList();
		deals = demandScope.scope(variantId, deals, demandAxisValue);
		// 신호등의 tier도 K를 탄다 — 판단 화면과 같은 K를 써야 "기준가는 있는데 신호는 회색"이 안 생긴다.
		BenchmarkView view = benchmark.compute(deals, currentPrice.currentPriceFor(variantId),
				PERIOD_MONTHS, params.of(variantId), clock);
		return signal.compute(deals, view, clock.instant(), FRESHNESS_LIMIT, QUALIFY_LIMIT);
	}
}
