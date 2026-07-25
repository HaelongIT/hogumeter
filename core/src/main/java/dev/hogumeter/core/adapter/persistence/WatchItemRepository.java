package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.watch.PinState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchItemRepository extends JpaRepository<WatchItemEntity, Long> {

	List<WatchItemEntity> findByDealEventId(Long dealEventId);

	Optional<WatchItemEntity> findByDealEventIdAndState(Long dealEventId, PinState state);

	List<WatchItemEntity> findByState(PinState state);
}
