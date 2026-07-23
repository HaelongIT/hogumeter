package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationEntity;
import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CMP-01 재료 — variant의 최신 쿠팡 관측 조회. 표시 계층은 이 값을 그대로 보여줄 뿐 합성하지 않는다. */
@Service
public class GetLatestCoupangPriceUseCase {

	private final CoupangPriceObservationRepository observations;

	public GetLatestCoupangPriceUseCase(CoupangPriceObservationRepository observations) {
		this.observations = observations;
	}

	@Transactional(readOnly = true)
	public Optional<CoupangPriceView> get(long variantId) {
		return observations.findTopByVariantIdOrderByObservedAtDesc(variantId)
				.map(GetLatestCoupangPriceUseCase::toView);
	}

	private static CoupangPriceView toView(CoupangPriceObservationEntity e) {
		return new CoupangPriceView(e.getRegularPrice(), e.getWowPrice(), e.getShippingFee(), e.getUrl(),
				e.getObservedAt());
	}
}
