package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
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

/** WATCH(docs/17) 핀 생성 — 자격(닫힌 목록)·유일성(딜당 활성 핀 1개) 둘 다 배선에서 지켜지는지. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PinDealUseCaseTest {

	@Autowired
	PinDealUseCase pinDeal;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	WatchItemRepository watchItems;

	private long variantId;

	private long deal(DealStatus status, OutlierFlag flag, boolean excluded) {
		if (variantId == 0) {
			long productId = products.save(new ProductEntity("핀 테스트", "test", DemandAxisMode.GROUPED)).getId();
			variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		}
		return dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, flag, excluded, status, Instant.parse("2026-07-01T00:00:00Z"),
				Instant.parse("2026-07-01T00:00:00Z"))).getId();
	}

	@Test
	void pinsAnEligibleDealAndRecordsAnAnchorPost() {
		long dealId = deal(DealStatus.ACTIVE, OutlierFlag.NONE, false);
		long postId = rawPosts.save(new RawDealPost("ppomppu", "p1", "https://ppomppu.test/1", "제목", 900_000L,
				Instant.now(), Instant.now(), "ACTIVE")).getId();
		sources.save(new DealEventSourceEntity(dealId, postId, "ppomppu"));

		long watchItemId = pinDeal.pin(dealId, "메모");

		WatchItemEntity saved = watchItems.findById(watchItemId).orElseThrow();
		assertThat(saved.getDealEventId()).isEqualTo(dealId);
		assertThat(saved.getNote()).isEqualTo("메모");
		assertThat(saved.getState()).isEqualTo(PinState.ACTIVE);
		assertThat(saved.getAnchorPostId()).isEqualTo(postId);
	}

	@Test
	void endedDealCannotBePinned() {
		long dealId = deal(DealStatus.ENDED, OutlierFlag.NONE, false);

		assertThatThrownBy(() -> pinDeal.pin(dealId, null)).isInstanceOf(DealNotPinnableException.class);
	}

	@Test
	void outlierFlaggedDealCannotBePinned() {
		long dealId = deal(DealStatus.ACTIVE, OutlierFlag.LOWER, false);

		assertThatThrownBy(() -> pinDeal.pin(dealId, null)).isInstanceOf(DealNotPinnableException.class);
	}

	@Test
	void aSecondActivePinOnTheSameDealIsRejected() {
		long dealId = deal(DealStatus.ACTIVE, OutlierFlag.NONE, false);
		pinDeal.pin(dealId, "first");

		assertThatThrownBy(() -> pinDeal.pin(dealId, "second"))
				.isInstanceOf(DealAlreadyPinnedException.class);
	}

	@Test
	void unknownDealIsRejected() {
		assertThatThrownBy(() -> pinDeal.pin(999_999_999L, null))
				.isInstanceOf(DealEventNotFoundException.class);
	}
}
