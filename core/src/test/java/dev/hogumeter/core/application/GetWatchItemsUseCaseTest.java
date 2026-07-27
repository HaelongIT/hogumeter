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
import dev.hogumeter.core.application.GetWatchItemsUseCase.WatchItemView;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 활성 탭·회고 탭 조회 — 결말(BOUGHT 등)에 닿은 핀은 활성에서 안 보이고 회고에서
 * 보이며, 딜의 현재가가 실제로 실린다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetWatchItemsUseCaseTest {

	@Autowired
	GetWatchItemsUseCase getWatchItems;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	WatchItemRepository watchItems;

	private long dealAt(long priceLast) {
		long productId = products.save(new ProductEntity("조회 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		return dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, priceLast,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
	}

	@Test
	void activeListShowsTheDealsCurrentPrice() {
		long dealId = dealAt(850_000);
		watchItems.save(new WatchItemEntity(dealId, null, "메모"));

		WatchItemView view = getWatchItems.active().stream()
				.filter(v -> v.dealEventId() == dealId).findFirst().orElseThrow();

		assertThat(view.currentPriceLast()).isEqualTo(850_000L);
		assertThat(view.note()).isEqualTo("메모");
		assertThat(view.state()).isEqualTo(PinState.ACTIVE);
		assertThat(view.resolvedAt()).isNull(); // 아직 결말 안 남
	}

	@Test
	void resolvedPinsDoNotAppearInTheActiveList() {
		long dealId = dealAt(900_000);
		WatchItemEntity item = watchItems.save(new WatchItemEntity(dealId, null, null));
		item.resolve(PinState.DROPPED, Instant.now());
		watchItems.save(item);

		List<Long> activeDealIds = getWatchItems.active().stream().map(WatchItemView::dealEventId).toList();

		assertThat(activeDealIds).doesNotContain(dealId);
	}

	@Test
	void resolvedListShowsOnlyResolvedPinsNewestFirst() {
		long boughtDeal = dealAt(800_000);
		WatchItemEntity bought = watchItems.save(new WatchItemEntity(boughtDeal, null, null));
		Instant older = Instant.now().minus(Duration.ofDays(2));
		bought.resolve(PinState.BOUGHT, older);
		watchItems.save(bought);

		long missedDeal = dealAt(810_000);
		WatchItemEntity missed = watchItems.save(new WatchItemEntity(missedDeal, null, null));
		Instant newer = Instant.now();
		missed.resolve(PinState.MISSED, newer);
		watchItems.save(missed);

		long activeDeal = dealAt(820_000);
		watchItems.save(new WatchItemEntity(activeDeal, null, null)); // ACTIVE — 회고에 안 나옴

		List<WatchItemView> resolved = getWatchItems.resolved();

		assertThat(resolved).extracting(WatchItemView::dealEventId)
				.containsExactly(missedDeal, boughtDeal); // 최근 결말 먼저
		assertThat(resolved).extracting(WatchItemView::state)
				.containsExactly(PinState.MISSED, PinState.BOUGHT);
		assertThat(resolved).noneMatch(v -> v.dealEventId() == activeDeal);
	}
}
