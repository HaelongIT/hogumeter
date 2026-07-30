package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemEntity;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** WATCH(docs/17) 부활 미응답 플래그 확인(Q-83 ⑤, 2026-07-30 확정) — 핀 상태 전이는 없다, 플래그만 내린다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AcknowledgeRevivalUseCaseTest {

	@Autowired
	AcknowledgeRevivalUseCase useCase;
	@Autowired
	WatchItemRepository watchItems;
	@Autowired
	DealEventRepository dealEvents;

	@Test
	void acknowledgingClearsTheFlagButNotTheState() {
		Instant now = Instant.parse("2026-07-01T00:00:00Z");
		long dealId = dealEvents.save(new DealEventEntity(null, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, now, now)).getId();
		WatchItemEntity saved = watchItems.save(new WatchItemEntity(dealId, null, null));
		saved.flagRevivalUnacknowledged();
		watchItems.saveAndFlush(saved);

		useCase.acknowledge(saved.getId());

		WatchItemEntity reloaded = watchItems.findById(saved.getId()).orElseThrow();
		assertThat(reloaded.isReviveUnacknowledged()).isFalse();
		assertThat(reloaded.getState()).isEqualTo(dev.hogumeter.core.domain.watch.PinState.ACTIVE);
	}

	@Test
	void unknownWatchItemIsRejected() {
		assertThatThrownBy(() -> useCase.acknowledge(999_999_999L))
				.isInstanceOf(WatchItemNotFoundException.class);
	}
}
