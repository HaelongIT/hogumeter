package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealEventSourceRepository extends JpaRepository<DealEventSourceEntity, Long> {

	List<DealEventSourceEntity> findByDealEventId(Long dealEventId);
}
