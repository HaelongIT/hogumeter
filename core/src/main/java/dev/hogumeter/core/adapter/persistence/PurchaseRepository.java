package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<PurchaseEntity, Long> {

	List<PurchaseEntity> findByVariantId(Long variantId);
}
