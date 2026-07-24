package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.ComputeDigestBestOpportunityUseCase.BestOpportunity;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 ① 딜 요약 배선. 상태별 잣대(활성 priceLast / 종료 priceMin)와 창·occurrenceSet 필터가
 * 실제 컬럼에서 도출되는지 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestBestOpportunityUseCaseTest {

	@Autowired
	ComputeDigestBestOpportunityUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;

	private long variantId;
	private final DigestWindow window = DigestWindow.of(
			Instant.parse("2026-07-17T20:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
			Instant.parse("2026-07-24T20:00:00Z"));

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("딜요약 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		variantId = variant.getId();
	}

	/** priceFirst, priceMin, priceMax, priceLast 순. firstSeen=lastSeen=firstSeen. */
	private void insertDeal(Instant firstSeen, long priceMin, long priceLast, DealStatus status,
			OutlierFlag flag, boolean excluded) {
		dealEvents.save(new DealEventEntity(variantId, false, null, priceLast, priceMin, priceLast, priceLast,
				Origin.LIVE, false, flag, excluded, status, firstSeen, firstSeen));
	}

	@Test
	void noQualifyingDealInWindowIsEmpty() {
		insertDeal(Instant.parse("2026-07-10T00:00:00Z"), 800_000, 850_000, DealStatus.ACTIVE, OutlierFlag.NONE, false);

		assertThat(useCase.bestOpportunity(variantId, window)).isEmpty();
	}

	@Test
	void activeDealUsesPriceLast() {
		// 활성: priceLast(850k)가 잣대. priceMin(700k)은 무시.
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), 700_000, 850_000, DealStatus.ACTIVE, OutlierFlag.NONE, false);

		BestOpportunity best = useCase.bestOpportunity(variantId, window).orElseThrow();

		assertThat(best.active()).isTrue();
		assertThat(best.opportunityPrice()).isEqualTo(850_000);
	}

	@Test
	void endedDealUsesPriceMin() {
		// 종료: priceMin(700k)이 잣대(지나간 기회). priceLast는 무시.
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), 700_000, 850_000, DealStatus.ENDED, OutlierFlag.NONE, false);

		BestOpportunity best = useCase.bestOpportunity(variantId, window).orElseThrow();

		assertThat(best.active()).isFalse();
		assertThat(best.opportunityPrice()).isEqualTo(700_000);
	}

	@Test
	void picksTheLowestOpportunityPriceAcrossMixedStatuses() {
		insertDeal(Instant.parse("2026-07-18T00:00:00Z"), 900_000, 920_000, DealStatus.ACTIVE, OutlierFlag.NONE, false);
		insertDeal(Instant.parse("2026-07-19T00:00:00Z"), 810_000, 990_000, DealStatus.ENDED, OutlierFlag.NONE, false); // 기회가 810k
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), 700_000, 880_000, DealStatus.ACTIVE, OutlierFlag.NONE, false); // 기회가 880k

		BestOpportunity best = useCase.bestOpportunity(variantId, window).orElseThrow();

		assertThat(best.opportunityPrice()).isEqualTo(810_000); // 종료 딜의 priceMin이 최저 기회
		assertThat(best.active()).isFalse();
	}

	@Test
	void upperOutlierAndRejectedLowerAreExcluded() {
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), 100_000, 100_000, DealStatus.ACTIVE, OutlierFlag.UPPER, false);
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), 200_000, 200_000, DealStatus.ACTIVE, OutlierFlag.LOWER, true);

		assertThat(useCase.bestOpportunity(variantId, window)).isEmpty();
	}

	@Test
	void missingVariantIsRejected() {
		assertThatThrownBy(() -> useCase.bestOpportunity(999_999L, window))
				.isInstanceOf(VariantNotFoundException.class);
	}

	@Test
	void returnsEmptyWhenNoDealsAtAll() {
		assertThat(useCase.bestOpportunity(variantId, window)).isEqualTo(Optional.empty());
	}
}
