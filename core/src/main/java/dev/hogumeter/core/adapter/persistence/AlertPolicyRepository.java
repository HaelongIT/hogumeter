package dev.hogumeter.core.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertPolicyRepository extends JpaRepository<AlertPolicyEntity, Long> {

	Optional<AlertPolicyEntity> findByVariantId(Long variantId);
}
