package dev.hogumeter.core.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HeldAlertRepository extends JpaRepository<HeldAlertEntity, Long> {

	boolean existsByDealEventId(Long dealEventId);
}
