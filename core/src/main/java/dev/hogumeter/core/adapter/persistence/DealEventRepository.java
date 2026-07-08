package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealEventRepository extends JpaRepository<DealEventEntity, Long> {

	List<DealEventEntity> findByVariantId(Long variantId);
}
