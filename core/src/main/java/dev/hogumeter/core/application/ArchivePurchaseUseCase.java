package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-06 아카이브 — 사람이 확정하는 두 갈래: 아카이브(CLOSED→ARCHIVED)/재활성(ARCHIVED→OBSERVING).
 *
 * <p><b>이 유스케이스가 생기기 전까지 ARCHIVED는 영원히 닿을 수 없었다.</b> {@link
 * dev.hogumeter.core.domain.purchase.Purchase#archive()}·{@code reactivate()}는 순수 도메인에
 * 있었지만 프로덕션 호출자가 0이었다(docs/91 Q-62) — Q-85가 배선한 "ARCHIVED면 🔥·목표가 억제"도
 * 이 배선 없이는 평생 발화할 일이 없는 죽은 안전망이었다.
 *
 * <p><b>자동 아카이브(다른 활성 관찰 없을 때)는 범위 밖</b>이다 — 그 조건 판정처는 아직 미정(Q-62
 * 재개 트리거). 여기는 사람이 직접 누르는 수동 결말만 연다. 상태기계 전이 승인은 도메인이 한다.
 *
 * <p>쓰기는 <b>벌크 UPDATE</b>다(state만) — {@link ExpirePurchaseObservationsUseCase}와 같은 수법.
 * 엔티티 전체 재작성은 PUR-02가 구매 시점에 동결한 스냅샷을 조용히 바꾼다.
 */
@Service
public class ArchivePurchaseUseCase {

	private final PurchaseRepository purchases;
	private final EntityManager entityManager;

	public ArchivePurchaseUseCase(PurchaseRepository purchases, EntityManager entityManager) {
		this.purchases = purchases;
		this.entityManager = entityManager;
	}

	@Transactional
	public void archive(long purchaseId) {
		transition(purchaseId, PurchaseState.ARCHIVED);
	}

	@Transactional
	public void reactivate(long purchaseId) {
		transition(purchaseId, PurchaseState.OBSERVING);
	}

	private void transition(long purchaseId, PurchaseState target) {
		PurchaseEntity entity = purchases.findById(purchaseId)
				.orElseThrow(() -> new PurchaseNotFoundException(purchaseId));
		PurchaseState current = entity.getState();
		PurchaseState next = (target == PurchaseState.ARCHIVED ? entity.toDomain().archive()
				: entity.toDomain().reactivate()).state(); // 전이 승인은 상태기계가 한다

		entityManager.createQuery("""
				update PurchaseEntity purchase
				   set purchase.state = :next
				 where purchase.id = :id
				   and purchase.state = :current
				""")
				.setParameter("next", next)
				.setParameter("id", purchaseId)
				.setParameter("current", current)
				.executeUpdate();
		entityManager.refresh(entity);
	}
}
