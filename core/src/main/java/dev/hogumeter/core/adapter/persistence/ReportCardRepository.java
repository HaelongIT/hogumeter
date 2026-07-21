package dev.hogumeter.core.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportCardRepository extends JpaRepository<ReportCardEntity, Long> {

	/** 이미 발급됐는지 — 발급은 멱등해야 한다(재발급 없음, ReportIssueGate). */
	boolean existsByPurchaseId(Long purchaseId);

	/** 발급된 성적표 조회(구매 상세 화면·검증). */
	Optional<ReportCardEntity> findByPurchaseId(Long purchaseId);
}
