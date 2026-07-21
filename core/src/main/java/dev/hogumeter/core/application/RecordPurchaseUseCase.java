package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.benchmark.BenchmarkCalculator;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-01/02 구매 기록. OBSERVING 상태로 저장하고, 구매 시점(purchasedAt) as-of 기준가를 재산출해
 * 스냅샷을 동결한다. as-of는 clock을 purchasedAt에 고정 → firstSeen이 그 이후인 딜은 창 밖으로 제외.
 * capturedAt 미보유라 "구매 전 게시·구매 후 수집" 딜은 as-of에 섞일 수 있음(docs/91 Q-32 한계).
 * observedFrom(관측 시작)은 잠정으로 최초 딜 firstSeen 사용(docs/91 Q-34).
 */
@Service
public class RecordPurchaseUseCase {

	private static final int DEFAULT_OBSERVATION_DAYS = 90; // PUR-01 기본
	private static final int PERIOD_MONTHS = 6; // Q-26 잠정(as-of 분석 창)

	private final VariantRepository variants;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final PurchaseRepository purchases;
	private final VariantDemandScope demandScope;
	private final VariantExcludeKeywords excludeKeywords;
	private final Clock clock;
	private final BenchmarkCalculator benchmark = new BenchmarkCalculator();
	private final BenchmarkParams params = BenchmarkParams.defaults();

	public RecordPurchaseUseCase(VariantRepository variants, DealEventRepository dealEvents, DealEventMapper mapper,
			PurchaseRepository purchases, VariantDemandScope demandScope, VariantExcludeKeywords excludeKeywords,
			Clock clock) {
		this.variants = variants;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.purchases = purchases;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.clock = clock;
	}

	@Transactional
	public long record(RecordPurchaseCommand cmd) {
		if (!variants.existsById(cmd.variantId())) {
			throw new VariantNotFoundException(cmd.variantId());
		}
		// 분리 제품이면 어느 수요축 값을 산 것인지 지정해야 한다(Q-66 ③, 확정본 "SPLIT 필수"). 안 그러면
		// 성적을 어느 색 분포에 대고 낼지 알 수 없다 — 저장 전에 막아 500 대신 400을 낸다(모드만 확인).
		demandScope.requireValueWhenSplit(cmd.variantId(), cmd.demandAxisValue());
		int observationDays = (cmd.observationDays() == null || cmd.observationDays() <= 0)
				? DEFAULT_OBSERVATION_DAYS : cmd.observationDays();
		Purchase purchase = new Purchase(cmd.variantId(), cmd.demandAxisValue(), cmd.paidPrice(),
				cmd.purchasedAt(), observationDays, cmd.linkedDealEventId(), PurchaseState.OBSERVING);

		Snapshot snapshot = freezeSnapshot(cmd);
		PurchaseEntity saved = purchases.save(new PurchaseEntity(purchase, snapshot));
		return saved.getId();
	}

	private Snapshot freezeSnapshot(RecordPurchaseCommand cmd) {
		// 제외 키워드(Q-28) → 수요축(Q-66) 순으로 표본을 좁힌다. 성적도 조회·알림과 같은 표본을 봐야
		// 사후에 "호구였나"가 같은 기준으로 답된다 — 리퍼가 섞인 기준가에 성적을 대면 어긋난다.
		List<DealEvent> deals = demandScope.scope(cmd.variantId(),
				excludeKeywords.filter(cmd.variantId(), dealEvents.findByVariantId(cmd.variantId())).stream()
						.map(mapper::toDomain).toList(),
				cmd.demandAxisValue());
		String basis = "P=" + PERIOD_MONTHS + "mo,K=" + params.kDisplay();

		Optional<Instant> observedFrom = deals.stream().map(DealEvent::firstSeen).min(Instant::compareTo);
		if (observedFrom.isPresent() && cmd.purchasedAt().isBefore(observedFrom.get())) {
			return Snapshot.unobserved(basis); // 관측 시작 이전 구매(PUR-02 UNOBSERVED)
		}
		Clock asOf = Clock.fixed(cmd.purchasedAt(), clock.getZone()); // purchasedAt을 '지금'으로 고정
		BenchmarkView view = benchmark.compute(deals, cmd.paidPrice(), PERIOD_MONTHS, params, asOf);
		return Snapshot.from(view, cmd.paidPrice(), basis);
	}
}
