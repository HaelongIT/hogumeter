package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationEntity;
import dev.hogumeter.core.adapter.persistence.CoupangPriceObservationRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** CMP-02 확장 ingest 유스케이스. 서버는 쿠팡에 절대 접근하지 않는다 — 받기만 한다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IngestCoupangObservationUseCaseTest {

	@Autowired
	IngestCoupangObservationUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	CoupangPriceObservationRepository observations;

	private long variantId;

	@BeforeEach
	void setUp() {
		Long productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
	}

	@Test
	void persistsAnObservationWithWowPriceAndShipping() {
		Instant now = Instant.parse("2026-07-23T00:00:00Z");
		CoupangObservationCommand cmd = new CoupangObservationCommand(variantId, 1_200_000L, 1_090_000L, 0L,
				"https://www.coupang.com/vp/products/1", now);

		long id = useCase.ingest(cmd);

		CoupangPriceObservationEntity saved = observations.findById(id).orElseThrow();
		assertThat(saved.getRegularPrice()).isEqualTo(1_200_000L);
		assertThat(saved.getWowPrice()).isEqualTo(1_090_000L);
		assertThat(saved.getShippingFee()).isZero();
	}

	@Test
	void wowPriceAndShippingCanBeAbsentRatherThanZero() {
		CoupangObservationCommand cmd = new CoupangObservationCommand(variantId, 1_200_000L, null, null,
				"https://www.coupang.com/vp/products/1", Instant.now());

		long id = useCase.ingest(cmd);

		CoupangPriceObservationEntity saved = observations.findById(id).orElseThrow();
		assertThat(saved.getWowPrice()).isNull(); // 0원이 아니라 "값 없음"
		assertThat(saved.getShippingFee()).isNull();
	}

	@Test
	void unknownVariantThrows() {
		CoupangObservationCommand cmd = new CoupangObservationCommand(999_999L, 1_000L, null, null,
				"https://www.coupang.com/vp/products/1", Instant.now());

		assertThatThrownBy(() -> useCase.ingest(cmd)).isInstanceOf(VariantNotFoundException.class);
	}

	@Test
	void nonPositivePriceIsRejectedNotStoredAsZero() {
		CoupangObservationCommand cmd = new CoupangObservationCommand(variantId, 0L, null, null,
				"https://www.coupang.com/vp/products/1", Instant.now());

		assertThatThrownBy(() -> useCase.ingest(cmd)).isInstanceOf(InvalidCoupangObservationException.class);
	}
}
