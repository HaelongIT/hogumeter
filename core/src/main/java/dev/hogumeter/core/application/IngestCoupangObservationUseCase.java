package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationEntity;
import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import org.springframework.stereotype.Service;

/**
 * CMP-02 확장 ingest 배선(SEC-04). <b>서버는 쿠팡에 절대 접근하지 않는다</b> — 확장이 사용자 브라우저에서
 * 읽어 보낸 값을 검증 후 저장할 뿐이다. 토큰 인증·레이트리밋은 어댑터(REST) 층의 몫(SEC-04 나머지 절반).
 */
@Service
public class IngestCoupangObservationUseCase {

	private final VariantRepository variants;
	private final CoupangPriceObservationRepository observations;

	public IngestCoupangObservationUseCase(VariantRepository variants,
			CoupangPriceObservationRepository observations) {
		this.variants = variants;
		this.observations = observations;
	}

	public long ingest(CoupangObservationCommand cmd) {
		if (!variants.existsById(cmd.variantId())) {
			throw new VariantNotFoundException(cmd.variantId());
		}
		if (cmd.regularPrice() <= 0) {
			throw new InvalidCoupangObservationException("가격은 0보다 커야 합니다: " + cmd.regularPrice());
		}
		CoupangPriceObservationEntity saved = observations.save(new CoupangPriceObservationEntity(cmd.variantId(),
				cmd.regularPrice(), cmd.wowPrice(), cmd.shippingFee(), cmd.url(), cmd.observedAt()));
		return saved.getId();
	}
}
