package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
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
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import dev.hogumeter.core.domain.digest.PinDigestEvent.PinDigestEventType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.watch.PinState;
import jakarta.persistence.EntityManager;
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
 * DIG-04 ④ 핀 결말 전이 + 부활 이벤트(docs/18, docs/91 Q-81) — "핀 이력 딜"(WatchItem 존재, 상태 무관)
 * 중 이번 창 안에 벌어진 사건만. 기각(DROPPED)은 원문이 제외.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestPinEndingsUseCaseTest {

	@Autowired
	ComputeDigestPinEndingsUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	WatchItemRepository watchItems;
	@Autowired
	DealAlertRepository alerts;
	@Autowired
	EntityManager entityManager;

	/** sent_at은 DB default(now()) — insertable=false라 같은 영속성 컨텍스트에선 refresh 전엔 null. */
	private Instant sentAtOf(DealAlertEntity saved) {
		entityManager.refresh(saved);
		return saved.getSentAt();
	}

	private long variantId;

	private long variant() {
		long productId = products.save(new ProductEntity("핀 다이제스트 테스트", "test", DemandAxisMode.GROUPED)).getId();
		return variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
	}

	private long dealIn(long variantId) {
		Instant now = Instant.now();
		return dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, now, now)).getId();
	}

	@Test
	void boughtPinWithinWindowYieldsBoughtEvent() {
		long v = variant();
		long dealId = dealIn(v);
		WatchItemEntity item = watchItems.save(new WatchItemEntity(dealId, null, null));
		Instant resolvedAt = Instant.now();
		item.resolve(PinState.BOUGHT, resolvedAt);
		watchItems.save(item);
		DigestWindow window = new DigestWindow(resolvedAt.minus(Duration.ofDays(1)), resolvedAt.plus(Duration.ofDays(1)));

		List<PinDigestEvent> events = useCase.pinEvents(v, window);

		assertThat(events).hasSize(1);
		assertThat(events.get(0).dealEventId()).isEqualTo(dealId);
		assertThat(events.get(0).type()).isEqualTo(PinDigestEventType.BOUGHT);
	}

	@Test
	void missedPinWithinWindowYieldsMissedEvent() {
		long v = variant();
		long dealId = dealIn(v);
		WatchItemEntity item = watchItems.save(new WatchItemEntity(dealId, null, null));
		Instant resolvedAt = Instant.now();
		item.resolve(PinState.MISSED, resolvedAt);
		watchItems.save(item);
		DigestWindow window = new DigestWindow(resolvedAt.minus(Duration.ofDays(1)), resolvedAt.plus(Duration.ofDays(1)));

		List<PinDigestEvent> events = useCase.pinEvents(v, window);

		assertThat(events).extracting(PinDigestEvent::type).containsExactly(PinDigestEventType.MISSED);
	}

	@Test
	void droppedPinIsExcluded() {
		long v = variant();
		long dealId = dealIn(v);
		WatchItemEntity item = watchItems.save(new WatchItemEntity(dealId, null, null));
		Instant resolvedAt = Instant.now();
		item.resolve(PinState.DROPPED, resolvedAt);
		watchItems.save(item);
		DigestWindow window = new DigestWindow(resolvedAt.minus(Duration.ofDays(1)), resolvedAt.plus(Duration.ofDays(1)));

		assertThat(useCase.pinEvents(v, window)).isEmpty();
	}

	@Test
	void resolutionOutsideWindowIsExcluded() {
		long v = variant();
		long dealId = dealIn(v);
		WatchItemEntity item = watchItems.save(new WatchItemEntity(dealId, null, null));
		Instant resolvedAt = Instant.now();
		item.resolve(PinState.BOUGHT, resolvedAt);
		watchItems.save(item);
		DigestWindow window = new DigestWindow(resolvedAt.plus(Duration.ofDays(1)), resolvedAt.plus(Duration.ofDays(2)));

		assertThat(useCase.pinEvents(v, window)).isEmpty();
	}

	@Test
	void reopenedFollowUpOnAPinnedDealYieldsRevivedEvent() {
		long v = variant();
		long dealId = dealIn(v);
		watchItems.save(new WatchItemEntity(dealId, null, null)); // 핀 이력만 있으면 됨(ACTIVE도 포함)
		DealAlertEntity saved = alerts.save(new DealAlertEntity(dealId, "REOPENED"));
		Instant sentAt = sentAtOf(saved);
		DigestWindow window = new DigestWindow(sentAt.minus(Duration.ofDays(1)), sentAt.plus(Duration.ofDays(1)));

		List<PinDigestEvent> events = useCase.pinEvents(v, window);

		assertThat(events).extracting(PinDigestEvent::type).containsExactly(PinDigestEventType.REVIVED);
	}

	@Test
	void reopenedFollowUpOnAnUnpinnedDealIsExcluded() {
		long v = variant();
		long dealId = dealIn(v); // 핀 이력 없음
		DealAlertEntity saved = alerts.save(new DealAlertEntity(dealId, "REOPENED"));
		Instant sentAt = sentAtOf(saved);
		DigestWindow window = new DigestWindow(sentAt.minus(Duration.ofDays(1)), sentAt.plus(Duration.ofDays(1)));

		assertThat(useCase.pinEvents(v, window)).isEmpty();
	}

	@Test
	void variantWithNoDealsYieldsNoEvents() {
		long v = variant();
		DigestWindow window = new DigestWindow(Instant.now().minus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1)));

		assertThat(useCase.pinEvents(v, window)).isEmpty();
	}
}
