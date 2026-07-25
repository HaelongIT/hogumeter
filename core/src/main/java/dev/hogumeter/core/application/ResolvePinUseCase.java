package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WATCH(docs/17) 핀 결말 — 사람이 확정하는 세 갈래: [샀어요]→BOUGHT / 기각·해제→DROPPED. ENDED 감지에
 * 의한 자동 MISSED는 이 유스케이스가 아니라 별도 배선이다("사실=자동, 판단=수동" — 사람이 누르는 버튼과
 * 시스템이 관측해 내는 사실은 진입 경로가 다르다).
 *
 * <p><b>PUR 프리필 없음(docs/91)</b>: docs/17은 BOUGHT 결말이 PUR 등록을 미리 채워 주길 바란다 — 그건
 * 등록 화면(REG)과의 교차 배선이라 아직 없다. 지금은 핀 상태만 정직하게 바꾼다.
 */
@Service
public class ResolvePinUseCase {

	private final WatchItemRepository watchItems;
	private final Clock clock;

	public ResolvePinUseCase(WatchItemRepository watchItems, Clock clock) {
		this.watchItems = watchItems;
		this.clock = clock;
	}

	@Transactional
	public void markBought(long watchItemId) {
		resolve(watchItemId, PinState.BOUGHT);
	}

	/** 기각·해제는 결과가 같다(docs/17) — 상태 하나(DROPPED)로 합친다. */
	@Transactional
	public void drop(long watchItemId) {
		resolve(watchItemId, PinState.DROPPED);
	}

	private void resolve(long watchItemId, PinState target) {
		WatchItemEntity item = watchItems.findById(watchItemId)
				.orElseThrow(() -> new WatchItemNotFoundException(watchItemId));
		item.resolve(target, clock.instant());
	}
}
