package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 핀 조회(읽기 전용). 활성 탭이 필요로 하는 최소 재료 — 딜이 사라졌으면(있을 수 없지만
 * 방어적으로) 가격·상태를 {@code null}로 그린다(지어내지 않는다).
 */
@Service
public class GetWatchItemsUseCase {

	private final WatchItemRepository watchItems;
	private final DealEventRepository dealEvents;

	public GetWatchItemsUseCase(WatchItemRepository watchItems, DealEventRepository dealEvents) {
		this.watchItems = watchItems;
		this.dealEvents = dealEvents;
	}

	@Transactional(readOnly = true)
	public List<WatchItemView> active() {
		return watchItems.findByState(PinState.ACTIVE).stream().map(this::toView).toList();
	}

	/** 회고 탭 — 결말(BOUGHT·MISSED·DROPPED)에 닿은 핀을 최근 결말 순으로. */
	@Transactional(readOnly = true)
	public List<WatchItemView> resolved() {
		return watchItems.findByStateNotOrderByResolvedAtDesc(PinState.ACTIVE).stream().map(this::toView).toList();
	}

	private WatchItemView toView(WatchItemEntity item) {
		DealEventEntity deal = dealEvents.findById(item.getDealEventId()).orElse(null);
		return new WatchItemView(item.getId(), item.getDealEventId(), item.getNote(), item.getState(),
				item.getCreatedAt(), item.getResolvedAt(), deal == null ? null : deal.getPriceLast(),
				deal == null ? null : deal.getStatus(), item.isReviveUnacknowledged());
	}

	public record WatchItemView(long watchItemId, long dealEventId, String note, PinState state, Instant pinnedAt,
			Instant resolvedAt, Long currentPriceLast, DealStatus dealStatus, boolean reviveUnacknowledged) {
	}
}
