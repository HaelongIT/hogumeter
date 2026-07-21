package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealIgnoreRepository extends JpaRepository<DealIgnoreEntity, Long> {

	boolean existsByDealEventId(Long dealEventId);

	List<DealIgnoreEntity> findByVariantId(Long variantId);
}
