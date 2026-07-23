package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** CMP-01의 재료 하나 — variant의 최신 쿠팡 관측. 여러 건이 쌓이면 <b>가장 최근</b>이 정본이다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetLatestCoupangPriceUseCaseTest {

	@Autowired
	GetLatestCoupangPriceUseCase useCase;
	@Autowired
	IngestCoupangObservationUseCase ingest;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;

	private long variantId;

	@BeforeEach
	void setUp() {
		Long productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
	}

	@Test
	void returnsTheMostRecentObservation() {
		Instant t1 = Instant.parse("2026-07-20T00:00:00Z");
		Instant t2 = t1.plus(Duration.ofDays(1));
		ingest.ingest(new CoupangObservationCommand(variantId, 1_200_000L, null, null, "https://c/1", t1));
		ingest.ingest(new CoupangObservationCommand(variantId, 1_150_000L, 1_090_000L, 0L, "https://c/1", t2));

		Optional<CoupangPriceView> latest = useCase.get(variantId);

		assertThat(latest).get().satisfies(v -> {
			assertThat(v.regularPrice()).isEqualTo(1_150_000L);
			assertThat(v.wowPrice()).isEqualTo(1_090_000L);
			assertThat(v.observedAt()).isEqualTo(t2);
		});
	}

	@Test
	void emptyWhenNoObservationYet() {
		assertThat(useCase.get(variantId)).isEmpty();
	}
}
