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
 * 제외 키워드(Q-28)·수요축 스코프(Q-66 ①)는 기준가·신호등과 같은 표본을 봐야 한다 — 리퍼 딜이나 다른 색이
 * 섞이면 판단 화면 바로 아래 줄이 위 기준가·신호등과 다른 사실을 말하게 된다.
 */
@Service
public class GetCadenceUseCase {

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final VariantDemandScope demandScope;
	private final VariantExcludeKeywords excludeKeywords;
	private final Clock clock;
	private final CadenceCalculator cadence = new CadenceCalculator();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	public GetCadenceUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			VariantDemandScope demandScope, VariantExcludeKeywords excludeKeywords, Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.clock = clock;
	}

	public CadenceView getCadence(long variantId, int periodMonths) {
		return getCadence(variantId, periodMonths, null);
	}

	/**
	 * @param demandAxisValue 분리(SPLIT) 제품에서 볼 수요축 값(Q-66 ①). 묶음이면 무시하고, 분리인데 없으면 거절한다.
	 */
	public CadenceView getCadence(long variantId, int periodMonths, String demandAxisValue) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = excludeKeywords.filter(variantId, dealEvents.findByVariantId(variantId)).stream()
				.map(mapper::toDomain)
				.toList();
		deals = demandScope.scope(variantId, deals, demandAxisValue);
		Instant observedFrom = deals.stream()
				.map(DealEvent::firstSeen)
				.min(Instant::compareTo)
				.orElseGet(clock::instant); // 잠정: 최초 딜 발생 = 관측 시작
		return cadence.compute(deals, observedFrom, periodMonths, params.kDisplay(), clock);
	}
}
