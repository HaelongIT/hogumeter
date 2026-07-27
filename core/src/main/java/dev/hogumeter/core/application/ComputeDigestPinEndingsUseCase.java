package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import dev.hogumeter.core.domain.digest.PinDigestEvent.PinDigestEventType;
import dev.hogumeter.core.domain.watch.PinState;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * DIG-04 ④ 핀 결말 전이 + 부활 이벤트(docs/18) — WATCH(M6) 채택 완료로 재개(docs/91 Q-81 트리거 충족).
 *
 * <p><b>"핀 이력 딜"</b>(WatchItem이 있는 딜, 상태 무관 — Q-83에서 쓰는 것과 같은 정의) 범위로 부활을
 * 좁힌다. DN-C1의 REOPENED 후속은 핀 여부와 무관하게 전 딜에 적용되지만(docs/17), 이 섹션은 "당신이
 * 지켜보던 것"만 요약하는 자리라 핀 이력 없는 딜의 부활은 여기 담지 않는다.
 *
 * <p>결말은 BOUGHT·MISSED만(기각 DROPPED는 원문이 제외). 판정 시각은 {@code WatchItem.resolvedAt}
 * (결말)·{@code deal_alert.sent_at}(부활) — 둘 다 창(반개구간)에 드는지로 이번 창 소속을 가른다.
 */
@Service
public class ComputeDigestPinEndingsUseCase {

	private static final String REOPENED = "REOPENED";

	private final DealEventRepository dealEvents;
	private final WatchItemRepository watchItems;
	private final DealAlertRepository alerts;

	public ComputeDigestPinEndingsUseCase(DealEventRepository dealEvents, WatchItemRepository watchItems,
			DealAlertRepository alerts) {
		this.dealEvents = dealEvents;
		this.watchItems = watchItems;
		this.alerts = alerts;
	}

	public List<PinDigestEvent> pinEvents(long variantId, DigestWindow window) {
		List<Long> dealIds = dealEvents.findByVariantId(variantId).stream().map(DealEventEntity::getId).toList();
		if (dealIds.isEmpty()) {
			return List.of();
		}

		List<WatchItemEntity> pinHistory = watchItems.findByDealEventIdIn(dealIds);
		List<PinDigestEvent> events = new ArrayList<>();
		for (WatchItemEntity item : pinHistory) {
			PinDigestEventType type = endingType(item.getState());
			if (type == null || item.getResolvedAt() == null || !window.contains(item.getResolvedAt())) {
				continue;
			}
			events.add(new PinDigestEvent(item.getDealEventId(), type, item.getResolvedAt()));
		}

		List<Long> pinnedDealIds = pinHistory.stream().map(WatchItemEntity::getDealEventId).distinct().toList();
		if (!pinnedDealIds.isEmpty()) {
			for (DealAlertEntity alert : alerts.findByDealEventIdInAndKind(pinnedDealIds, REOPENED)) {
				if (window.contains(alert.getSentAt())) {
					events.add(new PinDigestEvent(alert.getDealEventId(), PinDigestEventType.REVIVED, alert.getSentAt()));
				}
			}
		}
		return events;
	}

	private static PinDigestEventType endingType(PinState state) {
		return switch (state) {
			case BOUGHT -> PinDigestEventType.BOUGHT;
			case MISSED -> PinDigestEventType.MISSED;
			case ACTIVE, DROPPED -> null;
		};
	}
}
