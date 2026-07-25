package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.watch.PinEligibility;
import dev.hogumeter.core.domain.watch.PinState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 핀 생성. 자격({@link PinEligibility})과 유일성("딜당 활성 핀 1개")을 둘 다 지킨다.
 *
 * <p>{@code anchorPostId}는 "이벤트 재구성 시 자동 승계"의 재료다 — 지금은 그 승계 로직이 없어(딜이
 * merge로 재구성될 때 앵커를 옮기는 별도 배선이 필요, docs/91 다음 항목) 핀 시점의 소스 원문 중 하나를
 * 그대로 싣는다. 소스가 없으면(있을 수 없지만 방어적으로) null.
 */
@Service
public class PinDealUseCase {

	private final DealEventRepository dealEvents;
	private final DealEventSourceRepository sources;
	private final WatchItemRepository watchItems;

	public PinDealUseCase(DealEventRepository dealEvents, DealEventSourceRepository sources,
			WatchItemRepository watchItems) {
		this.dealEvents = dealEvents;
		this.sources = sources;
		this.watchItems = watchItems;
	}

	@Transactional
	public long pin(long dealEventId, String note) {
		DealEventEntity deal = dealEvents.findById(dealEventId)
				.orElseThrow(() -> new DealEventNotFoundException(dealEventId));
		if (!PinEligibility.canPin(deal.getStatus(), deal.getOutlierFlag(), deal.isPermanentlyExcluded())) {
			throw new DealNotPinnableException(dealEventId);
		}
		if (watchItems.findByDealEventIdAndState(dealEventId, PinState.ACTIVE).isPresent()) {
			throw new DealAlreadyPinnedException(dealEventId);
		}
		Long anchorPostId = sources.findByDealEventId(dealEventId).stream()
				.findFirst()
				.map(DealEventSourceEntity::getRawDealPostId)
				.orElse(null);
		WatchItemEntity saved = watchItems.save(new WatchItemEntity(dealEventId, anchorPostId, note));
		return saved.getId();
	}
}
