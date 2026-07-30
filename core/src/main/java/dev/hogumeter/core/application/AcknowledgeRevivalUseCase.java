package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 부활 미응답 플래그 확인(Q-83 ⑤, 2026-07-30 확정). 부활은 "전이 없음 + 플래그"라
 * ({@link IngestDealsUseCase}가 세운다) 확인도 전이 없이 플래그만 내린다 — 핀 상태는 그대로다.
 */
@Service
public class AcknowledgeRevivalUseCase {

	private final WatchItemRepository watchItems;

	public AcknowledgeRevivalUseCase(WatchItemRepository watchItems) {
		this.watchItems = watchItems;
	}

	@Transactional
	public void acknowledge(long watchItemId) {
		WatchItemEntity item = watchItems.findById(watchItemId)
				.orElseThrow(() -> new WatchItemNotFoundException(watchItemId));
		item.acknowledgeRevival();
	}
}
