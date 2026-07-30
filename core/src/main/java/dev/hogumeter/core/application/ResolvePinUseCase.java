package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 핀 결말 — 사람이 확정하는 세 갈래: [샀어요]→BOUGHT / 기각·해제→DROPPED. ENDED 감지에
 * 의한 자동 MISSED는 이 유스케이스가 아니라 별도 배선이다("사실=자동, 판단=수동" — 사람이 누르는 버튼과
 * 시스템이 관측해 내는 사실은 진입 경로가 다르다).
 *
 * <p><b>PUR 프리필(Q-83 ②, 2026-07-28 확정)</b>: BOUGHT 결말은 그 딜의 재료({@link BoughtPrefill})를
 * 낸다 — web이 판단 화면으로 이동해 구매 기록 폼을 채우는 데 쓴다. 미분류 딜(variant 없음)이면
 * {@code variantId}가 null이고 web은 이동하지 않는다(지어내지 않는다).
 */
@Service
public class ResolvePinUseCase {

	private final WatchItemRepository watchItems;
	private final DealEventRepository dealEvents;
	private final Clock clock;

	public ResolvePinUseCase(WatchItemRepository watchItems, DealEventRepository dealEvents, Clock clock) {
		this.watchItems = watchItems;
		this.dealEvents = dealEvents;
		this.clock = clock;
	}

	@Transactional
	public BoughtPrefill markBought(long watchItemId) {
		WatchItemEntity item = resolve(watchItemId, PinState.BOUGHT);
		DealEventEntity deal = dealEvents.findById(item.getDealEventId()).orElse(null);
		return new BoughtPrefill(deal == null ? null : deal.getVariantId(), item.getDealEventId(),
				deal == null ? null : deal.getPriceLast(),
				deal == null ? null : deal.getAppliedConditions());
	}

	/** 기각·해제는 결과가 같다(docs/17) — 상태 하나(DROPPED)로 합친다. */
	@Transactional
	public void drop(long watchItemId) {
		resolve(watchItemId, PinState.DROPPED);
	}

	private WatchItemEntity resolve(long watchItemId, PinState target) {
		WatchItemEntity item = watchItems.findById(watchItemId)
				.orElseThrow(() -> new WatchItemNotFoundException(watchItemId));
		item.resolve(target, clock.instant());
		return item;
	}

	/**
	 * PUR 프리필 재료(Q-83 ②). {@code variantId}·{@code dealPrice}는 미분류 딜·딜 행 부재 시 null —
	 * 값을 못 구하면 지어내지 않는다.
	 *
	 * @param appliedConditions 딜의 조건 태그(예: 배송비미상·카드할인) — web이 실지불가 안내를 여기서 가른다
	 */
	public record BoughtPrefill(Long variantId, long dealEventId, Long dealPrice, List<String> appliedConditions) {
	}
}
