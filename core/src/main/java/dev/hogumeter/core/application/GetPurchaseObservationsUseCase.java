package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.purchase.ObservationContextCalculator;
import dev.hogumeter.core.domain.purchase.Purchase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * PUR-05 관찰 문맥 조회(배선). variant의 저장된 Purchase마다 현재 딜로 관찰 문맥을 산출한다.
 * 딜은 variant 전체(수요축 필터는 C-6 후속 seam) — SIG·CAD와 동일 범위.
 */
@Service
public class GetPurchaseObservationsUseCase {

	private final VariantRepository variants;
	private final PurchaseRepository purchases;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final Clock clock;
	private final ObservationContextCalculator calculator = new ObservationContextCalculator();

	public GetPurchaseObservationsUseCase(VariantRepository variants, PurchaseRepository purchases,
			DealEventRepository dealEvents, DealEventMapper mapper, Clock clock) {
		this.variants = variants;
		this.purchases = purchases;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.clock = clock;
	}

	public List<PurchaseObservation> forVariant(long variantId) {
		if (!variants.existsById(variantId)) {
			throw new VariantNotFoundException(variantId);
		}
		List<DealEvent> deals = dealEvents.findByVariantId(variantId).stream().map(mapper::toDomain).toList();
		Instant now = clock.instant();
		return purchases.findByVariantId(variantId).stream()
				.map(entity -> {
					Purchase purchase = entity.toDomain();
					return new PurchaseObservation(entity.getId(), purchase.state(), purchase.paidPrice(),
							purchase.purchasedAt(), calculator.compute(purchase, deals, now));
				})
				.toList();
	}
}
