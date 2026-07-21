package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.ReportCardEntity;
import dev.hogumeter.core.adapter.persistence.ReportCardRepository;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.ReportCard;
import dev.hogumeter.core.domain.purchase.ReportCardCalculator;
import dev.hogumeter.core.domain.purchase.ReportIssueGate;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-04 성적표 발급 — {@code REPORT_PENDING} → {@code CLOSED}.
 *
 * <p><b>이 유스케이스가 생기기 전까지 구매는 REPORT_PENDING에서 영원히 멈췄다.</b> 순수 도메인
 * {@link ReportCardCalculator}는 성적을 계산할 줄 알고 테스트도 GREEN이었지만, 프로덕션에서
 * <b>부르는 사람이 하나도 없었다</b>(docs/91 Q-68의 거울상 — "호출자 0인 순수 함수"). 관찰 만료
 * ({@link ExpirePurchaseObservationsUseCase})가 REPORT_PENDING으로 옮겨 놓아도, 성적을 발급해
 * CLOSED로 닫을 손이 없어 화면은 "성적 집계 중"으로 영원히 멈춰 있었다(집계하는 코드가 없는데도).
 *
 * <p><b>발급은 quiet다</b>(PUR-04 {@link ReportIssueGate}) — 관통 알림을 보내지 않고 성적표만 저장한다.
 * 그래서 텔레그램 토큰과 무관하게 산다. 재발급 없음: {@code report_card.purchase_id}가 유니크이고
 * 이미 발급된 구매는 건너뛴다(멱등).
 *
 * <p><b>표본은 조회·알림·기록과 같아야 한다</b> — 제외 키워드(Q-28) → 수요축(Q-66) 순으로 좁힌 뒤
 * pricingSet을 본다. 그래야 사후 "호구였나"가 구매 시점 판단과 같은 기준으로 답된다
 * ({@link RecordPurchaseUseCase}와 동일 스코프). <b>기준가는 구매 시점 동결값</b>(PUR-02
 * {@code snap_benchmark_price})을 쓴다 — "그때의 판단 근거"라 재산출하지 않는다.
 *
 * <p><b>발급 게이트의 두 외부 조건은 보수적으로 참이다</b>(seam): 백필 배치(C-4)와 미분류 48h 유예는
 * 아직 배선 전이라 <b>대기할 외부 상태가 없다</b> — 그 상태가 생기면 여기서 판정해 주입한다(docs/91 Q-62).
 * observedFrom(관측 시작)·capturedAt(지각 백필 제외)의 잠정 처리는 {@link RecordPurchaseUseCase}와
 * 같다(docs/91 Q-34·Q-32).
 *
 * <p>쓰기는 <b>벌크 UPDATE</b>다({@code state}만) — {@code PurchaseEntity}에 상태 setter가 없고,
 * 엔티티 재작성은 PUR-02가 동결한 스냅샷을 조용히 바꾼다({@link ExpirePurchaseObservationsUseCase}와 같은 수법).
 */
@Service
public class IssuePendingReportCardsUseCase {

	private final PurchaseRepository purchases;
	private final ReportCardRepository reportCards;
	private final DealEventRepository dealEvents;
	private final DealEventMapper mapper;
	private final VariantDemandScope demandScope;
	private final VariantExcludeKeywords excludeKeywords;
	private final EntityManager entityManager;
	private final Clock clock;
	private final ReportCardCalculator calculator = new ReportCardCalculator();

	public IssuePendingReportCardsUseCase(PurchaseRepository purchases, ReportCardRepository reportCards,
			DealEventRepository dealEvents, DealEventMapper mapper, VariantDemandScope demandScope,
			VariantExcludeKeywords excludeKeywords, EntityManager entityManager, Clock clock) {
		this.purchases = purchases;
		this.reportCards = reportCards;
		this.dealEvents = dealEvents;
		this.mapper = mapper;
		this.demandScope = demandScope;
		this.excludeKeywords = excludeKeywords;
		this.entityManager = entityManager;
		this.clock = clock;
	}

	/**
	 * 성적 집계를 기다리는 구매마다 성적표를 발급하고 CLOSED로 닫는다. 매 주기 도는 작업이라 <b>멱등</b>하다 —
	 * 이미 발급된 것은 다시 발급하지 않고(유니크), 상태기계가 REPORT_PENDING이 아닌 것의 전이를 거부한다.
	 *
	 * @return 이번에 발급한 성적표 수. 0도 센다(OBS-02) — "발급 0건"과 "돌지 않았다"는 다른 사건이다.
	 */
	@Transactional
	public int issuePendingReportCards() {
		Instant now = clock.instant();
		List<PurchaseEntity> pending = purchases.findAll().stream()
			.filter(entity -> entity.getState() == PurchaseState.REPORT_PENDING)
			.toList();

		int issued = 0;
		for (PurchaseEntity entity : pending) {
			// PUR-04 발급 게이트: 관찰 만료 AND 배치 완료 AND 미분류 유예 종결. 뒤 둘은 아직 배선 전이라
			// 보수적으로 true(대기할 외부 상태가 없다) — 하드코딩이 아니라 게이트를 통과시켜 나중에 상태가
			// 생기면 여기서 막히게 둔다.
			boolean expired = entity.toDomain().isExpired(now);
			if (!ReportIssueGate.canIssue(expired, true, true)) {
				continue;
			}

			// 이미 발급됐으면 성적표는 그대로 두고 전이만 마저 한다(발급 후 전이 직전에 죽었던 경우 — 멱등·크래시 안전).
			if (!reportCards.existsByPurchaseId(entity.getId())) {
				reportCards.save(new ReportCardEntity(entity.getId(), compute(entity), now));
			}

			entityManager.createQuery("""
					update PurchaseEntity purchase
					   set purchase.state = :next
					 where purchase.id = :id
					   and purchase.state = :current
					""")
				.setParameter("next", entity.toDomain().close().state()) // 전이 승인은 상태기계가 한다
				.setParameter("id", entity.getId())
				.setParameter("current", PurchaseState.REPORT_PENDING)
				.executeUpdate();
			// 벌크 UPDATE는 영속성 컨텍스트를 우회한다 — 같은 tx에서 다시 읽으면 옛 상태가 나온다.
			entityManager.refresh(entity);
			issued++;
		}
		return issued;
	}

	private ReportCard compute(PurchaseEntity entity) {
		Purchase purchase = entity.toDomain();
		List<DealEvent> deals = demandScope.scope(purchase.variantId(),
				excludeKeywords.filter(purchase.variantId(), dealEvents.findByVariantId(purchase.variantId())).stream()
						.map(mapper::toDomain).toList(),
				purchase.demandAxisValue());

		Instant observationStart = purchase.purchasedAt();
		Instant observationEnd = purchase.observationEndsAt();
		// observedFrom(관측 시작) 잠정 = 최초 딜 firstSeen(RecordPurchaseUseCase와 동일, docs/91 Q-34).
		// 딜이 없으면 관찰 시작으로 둔다 — 그러면 계산기가 n=0(통계 없음)을 낸다(UNOBSERVED 아님).
		Instant observedFrom = deals.stream().map(DealEvent::firstSeen).min(Instant::compareTo).orElse(observationStart);
		// 기준가는 구매 시점 동결값(PUR-02) — 재산출하지 않는다. UNOBSERVED였으면 null이라 paidGap도 null.
		Long benchmarkPrice = entity.getSnapBenchmarkPrice();
		return calculator.compute(deals, purchase.paidPrice(), observationStart, observationEnd, observedFrom,
				benchmarkPrice);
	}
}
