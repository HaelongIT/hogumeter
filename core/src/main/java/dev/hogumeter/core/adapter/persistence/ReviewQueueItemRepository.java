package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewQueueItemRepository extends JpaRepository<ReviewQueueItemEntity, Long> {

	List<ReviewQueueItemEntity> findByType(ReviewQueueType type);
}
