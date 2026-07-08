package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.cadence.CadenceCalculator;
import dev.hogumeter.core.domain.cadence.CadenceView;
import dev.hogumeter.core.domain.deal.DealEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * CAD 딜 주기 조회(배선). 저장된 deal_event로 occurrenceSet 기반 주기를 산출(compute-on-demand).
 * observedFrom(관측 시작 미저장)은 잠정으로 최초 딜 firstSeen 사용 — 등록/백필 도달 시각 정착은 후속(docs/91 Q-34).
 */
@Service
public class GetCadenceUseCase {

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final Clock clock;
	private final CadenceCalculator cadence = new CadenceCalculator();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	public GetCadenceUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.clock = clock;
	}

	public CadenceView getCadence(long variantId, int periodMonths) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = dealEvents.findByVariantId(variantId).stream().map(mapper::toDomain).toList();
		Instant observedFrom = deals.stream()
				.map(DealEvent::firstSeen)
				.min(Instant::compareTo)
				.orElseGet(clock::instant); // 잠정: 최초 딜 발생 = 관측 시작
		return cadence.compute(deals, observedFrom, periodMonths, params.kDisplay(), clock);
	}
}
