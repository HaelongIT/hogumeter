package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.alert.AlertPolicy;
import dev.hogumeter.core.domain.benchmark.BenchmarkCalculator;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseTriggers;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 신규/변경 딜에 대한 알림 판정·발송(AL 배선). variant의 기준가·정책을 로드해 순수 도메인 디스패처에 넘긴다.
 * 정책이 없으면 기본(목표가·조용시간 없음, 기본 기간). 발송은 out-port 스텁(텔레그램 미발급).
 */
@Service
public class EvaluateAlertOnDealUseCase {

	private static final int DEFAULT_PERIOD_MONTHS = 6;

	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final AlertPolicyRepository policies;
	private final PurchaseRepository purchases;
	private final CurrentPriceProvider currentPrice;
	private final AlertDispatcher dispatcher;
	private final Clock clock;
	private final BenchmarkCalculator calculator = new BenchmarkCalculator();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	public EvaluateAlertOnDealUseCase(DealEventRepository dealEvents, DealEventMapper mapper,
			AlertPolicyRepository policies, PurchaseRepository purchases, CurrentPriceProvider currentPrice,
			AlertDispatcher dispatcher, Clock clock) {
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.policies = policies;
		this.purchases = purchases;
		this.currentPrice = currentPrice;
		this.dispatcher = dispatcher;
		this.clock = clock;
	}

	public DispatchOutcome evaluate(long variantId, DealEvent deal) {
		Optional<AlertPolicyEntity> policy = policies.findByVariantId(variantId);
		int periodMonths = policy.map(AlertPolicyEntity::getPeriodMonths).orElse(DEFAULT_PERIOD_MONTHS);
		AlertPolicy alertPolicy = policy
				.map(p -> new AlertPolicy(p.getTargetPrice(), p.getQuietHoursStart(), p.getQuietHoursEnd()))
				.orElseGet(() -> new AlertPolicy(null, null, null));

		List<DealEvent> deals = dealEvents.findByVariantId(variantId).stream()
				.map(mapper::toDomain)
				.toList();
		long current = currentPrice.currentPriceFor(variantId);
		BenchmarkView view = calculator.compute(deals, current, periodMonths, params, clock);

		List<Purchase> activePurchases = purchases.findByVariantId(variantId).stream()
				.map(p -> p.toDomain())
				.toList();
		boolean paidPriceFires = PurchaseTriggers.paidPriceTriggerFires(deal.priceFirst(), activePurchases);

		return dispatcher.dispatch(deal, view, alertPolicy, params, clock, paidPriceFires);
	}
}
