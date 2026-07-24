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
import dev.hogumeter.core.application.ComputeDigestOpportunityCountUseCase.OpportunityCount;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 ③ 관찰 경과("이번 창 +k / 누적 N")의 첫 배선. {@link dev.hogumeter.core.domain.dealset.DealSets}
 * 자체는 이미 순수 테스트가 있다 — 여기는 <b>컬럼→도메인→집계</b> 종단(가시화 시각=firstSeen 근사,
 * SPLIT 미분리)만 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestOpportunityCountUseCaseTest {

	@Autowired
	ComputeDigestOpportunityCountUseCase useCase;
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
		ProductEntity product = products.save(new ProductEntity("관찰경과 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		variantId = variant.getId();
	}

	private void insertDeal(Instant firstSeen, OutlierFlag flag, boolean permanentlyExcluded) {
		dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, flag, permanentlyExcluded, DealStatus.ACTIVE, firstSeen, firstSeen));
	}

	@Test
	void countsOnlyOccurrenceSetQualifyingDealsWithinTheWindow() {
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), OutlierFlag.NONE, false); // 창 안, 자격
		insertDeal(Instant.parse("2026-07-10T00:00:00Z"), OutlierFlag.NONE, false); // 창 밖(이전), 자격
		insertDeal(Instant.parse("2026-07-21T00:00:00Z"), OutlierFlag.UPPER, false); // 창 안이지만 UPPER 제외

		OpportunityCount count = useCase.count(variantId, window);

		assertThat(count.inWindow()).isEqualTo(1); // 창 안 + 자격 있는 것 1건뿐
		assertThat(count.cumulative()).isEqualTo(2); // UPPER만 빼면 전체 2건(창 안팎 무관)
	}

	@Test
	void rejectedLowerOutliersAreExcludedFromCumulativeToo() {
		insertDeal(Instant.parse("2026-07-20T00:00:00Z"), OutlierFlag.LOWER, true); // 기각된 LOWER

		OpportunityCount count = useCase.count(variantId, window);

		assertThat(count.inWindow()).isZero();
		assertThat(count.cumulative()).isZero();
	}

	@Test
	void zeroDealsIsZeroNotAbsent() {
		OpportunityCount count = useCase.count(variantId, window);

		assertThat(count.inWindow()).isZero();
		assertThat(count.cumulative()).isZero();
	}

	@Test
	void missingVariantIsRejected() {
		assertThatThrownBy(() -> useCase.count(999_999L, window)).isInstanceOf(VariantNotFoundException.class);
	}
}
