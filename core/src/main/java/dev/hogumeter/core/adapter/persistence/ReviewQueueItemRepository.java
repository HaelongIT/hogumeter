package dev.hogumeter.core.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewQueueItemRepository extends JpaRepository<ReviewQueueItemEntity, Long> {

	/** 같은 근거가 이미 큐에 있는가(Q-27 ④ dedup). 있으면 새 행 대신 재적재를 센다. */
	Optional<ReviewQueueItemEntity> findByDedupKey(String dedupKey);
}
