package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 결말 자동화 — "ENDED 감지→MISSED 자동(판정 금지, '종료됨(미구매)'일 뿐)". 사람이 누르는
 * BOUGHT/DROPPED({@link ResolvePinUseCase})와 진입 경로가 다르다 — 이건 관측된 <b>사실</b>이지 판단이
 * 아니다(원칙 "사실=자동, 판단=수동"). {@code PipelineScheduler}가 종료 재처리 다음에 부른다.
 */
@Service
public class MarkMissedPinsUseCase {

	private final WatchItemRepository watchItems;
	private final DealEventRepository dealEvents;
	private final Clock clock;

	public MarkMissedPinsUseCase(WatchItemRepository watchItems, DealEventRepository dealEvents, Clock clock) {
		this.watchItems = watchItems;
		this.dealEvents = dealEvents;
		this.clock = clock;
	}

	/** @return MISSED로 전이시킨 핀 수(OBS-02 — 0도 낸다). */
	@Transactional
	public int markEndedDealsAsMissed() {
		int missed = 0;
		for (WatchItemEntity item : watchItems.findByState(PinState.ACTIVE)) {
			boolean ended = dealEvents.findById(item.getDealEventId())
					.map(deal -> deal.getStatus() == DealStatus.ENDED)
					.orElse(false); // 딜이 사라졌으면 판단 근거가 없다 — 건드리지 않는다
			if (ended) {
				item.resolve(PinState.MISSED, clock.instant());
				missed++;
			}
		}
		return missed;
	}
}
