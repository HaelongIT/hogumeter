package dev.hogumeter.core.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoupangPriceObservationRepository extends JpaRepository<CoupangPriceObservationEntity, Long> {

	Optional<CoupangPriceObservationEntity> findTopByVariantIdOrderByObservedAtDesc(Long variantId);
}
