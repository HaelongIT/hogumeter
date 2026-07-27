package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.watch.PinState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchItemRepository extends JpaRepository<WatchItemEntity, Long> {

	List<WatchItemEntity> findByDealEventId(Long dealEventId);

	/** DIGEST ④(docs/91 Q-81) — 이 variant의 딜들 중 핀 이력이 있는 것만 한 번에 조회. */
	List<WatchItemEntity> findByDealEventIdIn(List<Long> dealEventIds);

	Optional<WatchItemEntity> findByDealEventIdAndState(Long dealEventId, PinState state);

	List<WatchItemEntity> findByState(PinState state);
}
