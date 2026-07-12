package dev.hogumeter.core.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DealAlertRepository extends JpaRepository<DealAlertEntity, Long> {

	/** 이 딜에 이 종류의 알림이 이미 나갔나 — 첫 알림 멱등·후속 재발송 방지(AL-03, Q-67). */
	boolean existsByDealEventIdAndKind(Long dealEventId, String kind);
}
