package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealAlertRepository extends JpaRepository<DealAlertEntity, Long> {

	/** 이 딜에 이 종류의 알림이 이미 나갔나 — 첫 알림 멱등·후속 재발송 방지(AL-03, Q-67). */
	boolean existsByDealEventIdAndKind(Long dealEventId, String kind);

	/** DIGEST ④ 부활 이벤트(REOPENED) — 핀 이력 딜 중 이번 창에 부활한 것만 골라내는 재료. */
	List<DealAlertEntity> findByDealEventIdInAndKind(List<Long> dealEventIds, String kind);
}
