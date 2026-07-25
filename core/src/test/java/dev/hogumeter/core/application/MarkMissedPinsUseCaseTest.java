package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 결말 자동화 — "ENDED 감지→MISSED 자동". 사람이 누르는 결말({@code ResolvePinUseCase})과
 * 진입 경로가 다르다는 것이 계약의 핵심이라, 여기선 <b>딜 상태만 보고 자동으로</b> 전이하는지를 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MarkMissedPinsUseCaseTest {

	@Autowired
	MarkMissedPinsUseCase markMissed;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	WatchItemRepository watchItems;

	private long pinnedDeal(DealStatus status) {
		long productId = products.save(new ProductEntity("미확인 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		long dealId = dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000,
				900_000, Origin.LIVE, false, OutlierFlag.NONE, false, status,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
		return watchItems.save(new WatchItemEntity(dealId, null, null)).getId();
	}

	@Test
	void activePinOnAnEndedDealBecomesMissed() {
		long watchItemId = pinnedDeal(DealStatus.ENDED);

		int missed = markMissed.markEndedDealsAsMissed();

		assertThat(missed).isEqualTo(1);
		assertThat(watchItems.findById(watchItemId).orElseThrow().getState()).isEqualTo(PinState.MISSED);
	}

	@Test
	void activePinOnALiveDealIsUntouched() {
		long watchItemId = pinnedDeal(DealStatus.ACTIVE);

		int missed = markMissed.markEndedDealsAsMissed();

		assertThat(missed).isZero();
		assertThat(watchItems.findById(watchItemId).orElseThrow().getState()).isEqualTo(PinState.ACTIVE);
	}
}
