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
