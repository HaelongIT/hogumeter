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
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.watch.IllegalPinTransitionException;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** WATCH(docs/17) 사람이 확정하는 결말 — [샀어요]→BOUGHT, 기각·해제→DROPPED. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ResolvePinUseCaseTest {

	@Autowired
	ResolvePinUseCase resolvePin;
	@Autowired
	WatchItemRepository watchItems;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;

	private long activePin() {
		long productId = products.save(new ProductEntity("결말 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		long dealId = dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000,
				900_000, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
		return watchItems.save(new WatchItemEntity(dealId, null, null)).getId();
	}

	@Test
	void markBoughtResolvesToTerminalStateWithTimestamp() {
		long id = activePin();

		resolvePin.markBought(id);

		WatchItemEntity saved = watchItems.findById(id).orElseThrow();
		assertThat(saved.getState()).isEqualTo(PinState.BOUGHT);
		assertThat(saved.getResolvedAt()).isNotNull();
	}

	/** PUR 프리필(Q-83 ②, 2026-07-28 확정) 재료 — web이 판단 화면 폼을 채우는 데 쓴다. */
	@Test
	void markBoughtReturnsPrefillMaterialFromTheLinkedDeal() {
		long productId = products.save(new ProductEntity("프리필 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		long dealId = dealEvents.save(new DealEventEntity(variantId, false, null, 850_000, 850_000, 850_000,
				850_000, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
		long id = watchItems.save(new WatchItemEntity(dealId, null, null)).getId();

		ResolvePinUseCase.BoughtPrefill prefill = resolvePin.markBought(id);

		assertThat(prefill.variantId()).isEqualTo(variantId);
		assertThat(prefill.dealEventId()).isEqualTo(dealId);
		assertThat(prefill.dealPrice()).isEqualTo(850_000L);
	}

	/** 미분류 딜(variant 없음)은 프리필 재료를 지어내지 않는다 — null 그대로. */
	@Test
	void markBoughtPrefillIsNullWhenTheDealIsUnclassified() {
		long dealId = dealEvents.save(new DealEventEntity(null, true, null, 850_000, 850_000, 850_000, 850_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
		long id = watchItems.save(new WatchItemEntity(dealId, null, null)).getId();

		ResolvePinUseCase.BoughtPrefill prefill = resolvePin.markBought(id);

		assertThat(prefill.variantId()).isNull();
		assertThat(prefill.dealPrice()).isEqualTo(850_000L);
	}

	@Test
	void dropResolvesToDropped() {
		long id = activePin();

		resolvePin.drop(id);

		assertThat(watchItems.findById(id).orElseThrow().getState()).isEqualTo(PinState.DROPPED);
	}

	@Test
	void resolvingAnAlreadyResolvedPinIsRejected() {
		long id = activePin();
		resolvePin.markBought(id);

		assertThatThrownBy(() -> resolvePin.drop(id)).isInstanceOf(IllegalPinTransitionException.class);
	}

	@Test
	void unknownWatchItemIsRejected() {
		assertThatThrownBy(() -> resolvePin.markBought(999_999_999L))
				.isInstanceOf(WatchItemNotFoundException.class);
	}
}
