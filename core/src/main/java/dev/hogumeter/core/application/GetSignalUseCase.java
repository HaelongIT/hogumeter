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
import java.util.ArrayList;
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
	private final ObservationClock observationClock;
	private final Clock clock;
	private final BenchmarkCalculator benchmark = new BenchmarkCalculator();
	private final SignalCalculator signal = new SignalCalculator();

	public GetSignalUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			CurrentPriceProvider currentPrice, VariantBenchmarkParams params, VariantDemandScope demandScope,
			VariantExcludeKeywords excludeKeywords, ObservationClock observationClock, Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.currentPrice = currentPrice;
		this.params = params;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.observationClock = observationClock;
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
		VariantExcludeKeywords.Filtered filtered = excludeKeywords.filterCounting(variantId,
				dealEvents.findByVariantId(variantId));
		List<DealEvent> deals = filtered.kept().stream().map(mapper::toDomain).toList();
		deals = demandScope.scope(variantId, deals, demandAxisValue);
		// 신호등의 tier도 K를 탄다 — 판단 화면과 같은 K를 써야 "기준가는 있는데 신호는 회색"이 안 생긴다.
		BenchmarkView view = benchmark.compute(deals, currentPrice.currentPriceFor(variantId),
				PERIOD_MONTHS, params.of(variantId), clock);
		ObservationClock.Reading observed = observationClock.read();
		SignalView signalView = signal.compute(deals, view, observed.at(), FRESHNESS_LIMIT, QUALIFY_LIMIT);
		return withUnmeasuredClockNote(withExclusionNote(signalView, filtered.excluded()), observed);
	}

	/**
	 * 제외 키워드가 실제로 뺀 건수를 <b>딱지로 노출</b>한다 — 제외는 조용해서, 세어 보여주지 않으면
	 * "원래 딜이 없었다"와 구별되지 않는다. 특히 <b>전역</b> 키워드(Q-28 ①)가 너무 넓으면 모든 제품의
	 * 표본을 한꺼번에 갉아먹는데 이 딱지가 없으면 아무도 모른다. <b>0이면 딱지를 달지 않는다</b> —
	 * 아무것도 안 걸렀다는 사실까지 화면을 어지럽힐 이유는 없다(로그·카운터와 달리 여긴 사람의 눈이다).
	 */
	/**
	 * 폴링 기록이 없어 벽시계로 신선도를 잰 경우 그 사실을 딱지로 낸다. 신선도가 <b>측정된 것처럼</b>
	 * 보이면 "수집이 멈춘 적 없다"는 거짓말이 된다 — 색은 그대로 두되 근거의 출처를 밝힌다.
	 */
	private static SignalView withUnmeasuredClockNote(SignalView view, ObservationClock.Reading observed) {
		if (observed.measured()) {
			return view;
		}
		List<String> notes = new ArrayList<>(view.notes());
		notes.add("수집 기록 없음(신선도는 현재 시각 기준)");
		return new SignalView(view.color(), view.goodDealLineEstablished(), notes);
	}

	private static SignalView withExclusionNote(SignalView view, int excluded) {
		if (excluded <= 0) {
			return view;
		}
		List<String> notes = new ArrayList<>(view.notes());
		notes.add("제외 키워드로 " + excluded + "건 제외");
		return new SignalView(view.color(), view.goodDealLineEstablished(), notes);
	}
}
