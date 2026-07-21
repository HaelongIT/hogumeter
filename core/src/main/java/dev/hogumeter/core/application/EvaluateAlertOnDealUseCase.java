package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.application.port.out.CurrentPriceProvider;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.alert.AlertPolicy;
import dev.hogumeter.core.domain.benchmark.BenchmarkCalculator;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.product.DemandAxisMode;
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
	private final DealAlertRepository alerts;
	private final VariantDemandScope demandScope;
	private final VariantExcludeKeywords excludeKeywords;
	private final Clock clock;
	private final BenchmarkCalculator calculator = new BenchmarkCalculator();

	public EvaluateAlertOnDealUseCase(DealEventRepository dealEvents, DealEventMapper mapper,
			AlertPolicyRepository policies, PurchaseRepository purchases, CurrentPriceProvider currentPrice,
			AlertDispatcher dispatcher, DealAlertRepository alerts, VariantDemandScope demandScope,
			VariantExcludeKeywords excludeKeywords, Clock clock) {
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.policies = policies;
		this.purchases = purchases;
		this.currentPrice = currentPrice;
		this.dispatcher = dispatcher;
		this.alerts = alerts;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.clock = clock;
	}

	public DispatchOutcome evaluate(long variantId, long dealEventId, DealEvent deal) {
		Optional<AlertPolicyEntity> policy = policies.findByVariantId(variantId);
		int periodMonths = policy.map(AlertPolicyEntity::getPeriodMonths).orElse(DEFAULT_PERIOD_MONTHS);
		// 이미 읽은 정책으로 파라미터를 만든다(중복 조회 회피). 해석은 VariantBenchmarkParams 한 곳에만 있다.
		BenchmarkParams params = VariantBenchmarkParams.from(policy);
		AlertPolicy alertPolicy = policy
				.map(p -> new AlertPolicy(p.getTargetPrice(), p.getQuietHoursStart(), p.getQuietHoursEnd()))
				.orElseGet(() -> new AlertPolicy(null, null, null));

		// 분리 제품이면 **이 딜과 같은 수요축 값**의 분포로 판정한다(Q-66 ①) — 전체로 판정하면 그게 묶음의
		// 거짓말이다(블랙 딜을 화이트가 섞인 기준가에 대는 셈). 값 미상 딜은 어느 분포에도 댈 수 없으므로
		// 판단하지 않는다 — 지어낸 기준으로 알림을 내느니 조용한 게 낫고, 사람이 큐에서 분류한다(확정본 §41).
		if (demandScope.modeOf(variantId) == DemandAxisMode.SPLIT && deal.demandAxisValue() == null) {
			return DispatchOutcome.NO_ALERT;
		}
		// 제외 키워드에 걸리는 딜은 기준가 표본에서 뺀다(Q-28) — 리퍼가 섞인 기준가로 알림을 내면 안 된다.
		List<DealEvent> deals = demandScope.scope(variantId,
				excludeKeywords.filter(variantId, dealEvents.findByVariantId(variantId)).stream()
						.map(mapper::toDomain)
						.toList(),
				deal.demandAxisValue());
		Long current = currentPrice.currentPriceFor(variantId); // 미확립이면 null(Q-53)
		BenchmarkView view = calculator.compute(deals, current, periodMonths, params, clock);

		List<Purchase> activePurchases = purchases.findByVariantId(variantId).stream()
				.map(p -> p.toDomain())
				.toList();
		boolean paidPriceFires = PurchaseTriggers.paidPriceTriggerFires(deal.priceFirst(), activePurchases);

		DispatchOutcome outcome = dispatcher.dispatch(deal, view, alertPolicy, params, clock, paidPriceFires);
		// AL-03: 첫 알림이 실제로 나갔으면 이력에 FIRST를 남긴다 — 후속은 이 FIRST가 있는 딜에만 보낸다(Q-67). 멱등.
		if (outcome == DispatchOutcome.SENT && !alerts.existsByDealEventIdAndKind(dealEventId, DealAlertEntity.FIRST)) {
			alerts.save(new DealAlertEntity(dealEventId, DealAlertEntity.FIRST));
		}
		return outcome;
	}
}
