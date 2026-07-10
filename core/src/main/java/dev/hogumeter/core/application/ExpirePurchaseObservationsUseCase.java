package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-01 관찰 만료 — {@code OBSERVING} → {@code REPORT_PENDING}.
 *
 * <p><b>이 유스케이스가 생기기 전까지 관찰은 영원히 끝나지 않았다.</b> {@link Purchase#expire()}와
 * {@link Purchase#isExpired(Instant)}는 순수 도메인에 있고 테스트도 GREEN이었지만, 프로덕션에서
 * {@code purchase.state}를 쓰는 곳은 {@link RecordPurchaseUseCase} 하나뿐이고 언제나 {@code OBSERVING}을
 * 썼다. 상태기계에 전이가 있어도 <b>부르는 사람이 없으면 그 전이는 존재하지 않는다.</b>
 *
 * <p>그 결과 셋: ① "관찰 N일차"가 무한히 커진다 ② PUR-03 "산 뒤 알림"(paidPrice 하회)은
 * {@code OBSERVING}에만 발화하므로 <b>3년 전 구매에도 계속 알림이 나갔을 것이다</b>
 * ③ 성적표 대기로 넘어가지 않는다.
 *
 * <p><b>전이 판정은 도메인이 한다.</b> 여기서 {@code REPORT_PENDING}을 하드코딩하지 않고
 * {@code purchase.expire()}가 돌려준 상태를 쓴다 — 상태기계가 허용하지 않으면 예외가 난다.
 *
 * <p>쓰기는 <b>벌크 UPDATE</b>다. {@code PurchaseEntity}에는 상태 setter가 없고(다른 개발자 소유 파일),
 * delete+insert나 엔티티 전체 재작성은 PUR-02가 <b>구매 시점에 동결</b>한 스냅샷을 조용히 바꾼다.
 * 우리가 아는 컬럼 하나(`state`)만 건드린다.
 *
 * <p>1인용 규모라 전량 조회 후 메모리에서 거른다(과최적화 금지, PERF-04). {@code PurchaseRepository}에
 * {@code findByState}를 더하려면 기존 파일을 고쳐야 한다.
 */
@Service
public class ExpirePurchaseObservationsUseCase {

	private final PurchaseRepository purchases;
	private final EntityManager entityManager;
	private final Clock clock;

	public ExpirePurchaseObservationsUseCase(PurchaseRepository purchases, EntityManager entityManager, Clock clock) {
		this.purchases = purchases;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * 관찰 기간이 끝난 구매를 성적 집계 대기로 옮긴다. 매 주기 도는 작업이라 <b>멱등</b>해야 한다 —
	 * 이미 넘어간 것은 다시 전이시키지 않는다(상태기계가 예외를 던진다).
	 *
	 * @return 이번에 만료시킨 건수. 0도 센다(OBS-02) — "만료 0건"과 "돌지 않았다"는 다른 사건이다.
	 */
	@Transactional
	public int expireDueObservations() {
		Instant now = clock.instant();
		List<PurchaseEntity> due = purchases.findAll().stream()
			.filter(entity -> entity.getState() == PurchaseState.OBSERVING)
			.filter(entity -> entity.toDomain().isExpired(now))
			.toList();
		if (due.isEmpty()) {
			return 0;
		}

		for (PurchaseEntity entity : due) {
			PurchaseState next = entity.toDomain().expire().state(); // 전이 승인은 상태기계가 한다
			entityManager.createQuery("""
					update PurchaseEntity purchase
					   set purchase.state = :next
					 where purchase.id = :id
					   and purchase.state = :current
					""")
				.setParameter("next", next)
				.setParameter("id", entity.getId())
				.setParameter("current", PurchaseState.OBSERVING)
				.executeUpdate();
			// 벌크 UPDATE는 영속성 컨텍스트를 우회한다 — 같은 tx에서 다시 읽으면 옛 상태가 나온다.
			entityManager.refresh(entity);
		}
		return due.size();
	}
}
