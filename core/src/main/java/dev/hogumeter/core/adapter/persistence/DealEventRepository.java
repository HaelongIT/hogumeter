package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.deal.DealStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealEventRepository extends JpaRepository<DealEventEntity, Long> {

	List<DealEventEntity> findByVariantId(Long variantId);

	/** Q-27 상태 재처리 입력 — 종료 가능 상태(ACTIVE·VERIFIED)의 딜만. */
	List<DealEventEntity> findByStatusIn(Collection<DealStatus> statuses);
}
