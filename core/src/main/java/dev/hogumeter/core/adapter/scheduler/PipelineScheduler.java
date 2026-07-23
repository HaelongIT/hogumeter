package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.application.ExpirePurchaseObservationsUseCase;
import dev.hogumeter.core.application.FlushHeldAlertsUseCase;
import dev.hogumeter.core.application.FlushHeldAlertsUseCase.FlushReport;
import dev.hogumeter.core.application.FollowUpAlertUseCase;
import dev.hogumeter.core.application.FoldUsedListingsUseCase;
import dev.hogumeter.core.application.FoldUsedListingsUseCase.FoldReport;
import dev.hogumeter.core.application.IngestDealsUseCase;
import dev.hogumeter.core.application.IngestReport;
import dev.hogumeter.core.application.IssuePendingReportCardsUseCase;
import dev.hogumeter.core.application.PipelineHealthMonitor;
import dev.hogumeter.core.application.PreserveAppliedConditionsUseCase;
import dev.hogumeter.core.application.ReprocessDealPricesUseCase;
import dev.hogumeter.core.application.ReprocessDealStatusUseCase;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.DealTags;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 파이프라인 트리거 — {@code raw_deal_post}를 주기적으로 소비한다.
 *
 * <p><b>이게 없으면 시스템은 죽어 있다.</b> collector가 원문을 아무리 적재해도
 * {@link IngestDealsUseCase#ingestPending()}을 부르는 사람이 없으면 {@code deal_event}가 생기지 않고,
 * 기준가 표본은 영원히 0이며 알림도 오지 않는다(2026-07-10까지 실제로 그랬다 — docs/91 Q-27 ⑤).
 *
 * <p>순서는 <b>관찰만료 → 성적표발급 → ingest → 조건태그 → 가격 → 종료 → 후속알림</b>이다.
 * <ol>
 * <li>관찰만료: 관찰 기간이 끝난 구매를 REPORT_PENDING으로(PUR-01). <b>ingest보다 먼저</b> 온다 —
 * ingest는 새 딜마다 알림을 태우는데 PUR-03의 "산 뒤 알림"은 OBSERVING 관찰에만 발화한다.
 * 나중에 돌리면 이미 끝난 관찰이 이번 틱의 딜에 대해 한 번 더 알림을 낸다.</li>
 * <li>성적표발급: REPORT_PENDING 구매에 성적표(PUR-04)를 발급하고 CLOSED로 닫는다. <b>만료 바로 뒤</b>에 와서
 * 방금 만료된 것까지 같은 틱에 닫는다 — 그러면 뒤의 ingest가 CLOSED로 보아 "산 뒤 알림"에서 자연히 뺀다.
 * 발급은 quiet(관통 알림 없음)라 텔레그램 토큰과 무관하다. 관찰창은 과거라 이번 틱의 새 딜은 성적에 안 섞인다.</li>
 * <li>ingest: 새 원문을 딜로 만든다. 이번 주기에 링크된 것까지 아래 단계들이 보게 한다.</li>
 * <li>조건 태그: 원문의 조건부 가격 태그를 딜로 끌어올린다(BM-02 AC-2). <b>ingest 바로 뒤</b>에 온다 —
 * 방금 링크된 원문의 태그가 같은 틱에 보존된다. 이게 없어 표본의 약 1할이 무조건 가격 행세를 했다.</li>
 * <li>가격: 이미 링크된 원문의 새 가격을 딜에 반영한다(BM-01 AC-2, Q-27 ①).</li>
 * <li>종료: 링크된 원문이 전부 종료됐으면 딜을 ENDED로. <b>가격보다 뒤에 온다</b> — 종료된 딜의
 * 가격은 더 이상 갱신되지 않으므로, 종료 직전의 마지막 가격까지 반영하고 닫는다.</li>
 * <li>후속알림: 이번 틱에 가격변화·종료·<b>병합(교차검증)</b>한 딜 중 <b>첫 알림이 나갔던 것</b>에만 후속을
 * 보낸다(AL-03, Q-67·Q-13). 가격·종료 재처리·ingest의 병합 결과가 낸 전이 id를 그대로 종류별로 흘려보낸다 —
 * 종료가 마지막이라 닫히기 직전 값까지 반영된다. VERIFIED는 ingest 단계에서 이미 정해지므로 순서상
 * 앞당겨도 되지만, 세 후속을 한자리에 모아 두는 게 읽기 쉬워 여기 둔다.</li>
 * </ol>
 *
 * <p>기동 직후에는 돌지 않는다({@code initialDelay = interval}). {@code fixedDelay}는 기본적으로
 * 시작하자마자 1회 실행되는데, 그러면 {@code @SpringBootTest}들이 스케줄러에 오염된다.
 *
 * <p>매 틱마다 {@link PipelineTickReport}를 남긴다(OBS-02). 조용히 도는 스케줄러는 아무것도 처리하지
 * 않는 스케줄러와 구별되지 않는다.
 */
@Component
@ConditionalOnProperty(name = "core.pipeline.enabled", havingValue = "true", matchIfMissing = true)
public class PipelineScheduler {

	private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

	private final Runnable expireObservations;
	private final Supplier<Integer> issueReportCards; // 성적표 발급(PUR-04) — 발급 수를 붙잡아 리포트에 싣는다
	private final Supplier<IngestReport> ingest;
	private final Runnable preserveConditions;
	private final Supplier<List<Long>> reprocessPrices;
	private final Supplier<List<Long>> reprocessStatus;
	private final BiFunction<List<Long>, FollowUpKind, Integer> followUp;
	private final Supplier<FoldReport> foldUsedListings; // USED-02 목록 스냅샷 접기 + 생애주기 알림(USED-03)
	private final Supplier<FlushReport> flushHeld; // 방해금지 종료분 재평가·발송(Q-20 ②)
	private final Consumer<Boolean> healthTick; // 틱 건강 여부 → 연속 실패 시 관리 알림(OBS-03, Q-56)
	private final Supplier<PipelineSnapshot> probe;
	private final Consumer<PipelineTickReport> report;

	// 이번 틱에 예외를 던진 단계 수(Q-56). runStep이 실패를 삼키므로 여기서 세지 않으면 침묵한다.
	// @Scheduled(fixedDelay)는 틱이 겹치지 않으므로(이전 틱 완료 후에야 다음이 시작) 필드 하나로 안전하다.
	private int stepFailures;

	@Autowired
	PipelineScheduler(ExpirePurchaseObservationsUseCase expireObservations,
			IssuePendingReportCardsUseCase issueReportCards, IngestDealsUseCase ingest,
			PreserveAppliedConditionsUseCase conditions, ReprocessDealPricesUseCase prices,
			ReprocessDealStatusUseCase status, FollowUpAlertUseCase followUp, FlushHeldAlertsUseCase flushHeld,
			FoldUsedListingsUseCase foldUsedListings,
			PipelineHealthMonitor healthMonitor, RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, ReviewQueueItemRepository reviewQueue, PurchaseRepository purchases,
			JdbcTemplate jdbc) {
		this(expireObservations::expireDueObservations, issueReportCards::issuePendingReportCards,
				ingest::ingestPending, conditions::preserveTags,
				prices::reprocessPriceChanges, status::reprocessEndedDeals, followUp::sendFollowUps, flushHeld::flush,
				foldUsedListings::foldPending, healthMonitor::onTick,
				() -> new PipelineSnapshot(
						rawPosts.count(),
						sources.count(),
						dealEvents.count(),
						reviewQueue.count(),
						dealEvents.findByStatusIn(List.of(DealStatus.ENDED)).size(),
						rawPosts.findUnprocessed().size(),
						purchases.findAll().stream()
								.filter(p -> p.getState() == PurchaseState.REPORT_PENDING)
								.count(),
						// DealEventEntity는 applied_conditions를 매핑하지 않는다(상대 소유). SQL로 센다.
						jdbc.queryForObject(
								"select count(*) from deal_event where cardinality(applied_conditions) > 0",
								Long.class),
						// 표식의 정본은 collector다(scripts/check-tag-contract.sh가 두 리터럴을 묶는다).
						jdbc.queryForObject(
								"select count(*) from deal_event where ? = any(applied_conditions)",
								Long.class, DealTags.SHIPPING_UNKNOWN)),
				tick -> log.info("pipeline tick {}", tick));
	}

	/** 테스트 seam(플러시·건강 포함) — 이 프로젝트 테스트는 mock 대신 실객체·람다를 쓴다. */
	PipelineScheduler(Runnable expireObservations, Supplier<Integer> issueReportCards, Supplier<IngestReport> ingest,
			Runnable preserveConditions, Supplier<List<Long>> reprocessPrices, Supplier<List<Long>> reprocessStatus,
			BiFunction<List<Long>, FollowUpKind, Integer> followUp, Supplier<FlushReport> flushHeld,
			Supplier<FoldReport> foldUsedListings, Consumer<Boolean> healthTick, Supplier<PipelineSnapshot> probe,
			Consumer<PipelineTickReport> report) {
		this.expireObservations = expireObservations;
		this.issueReportCards = issueReportCards;
		this.ingest = ingest;
		this.preserveConditions = preserveConditions;
		this.reprocessPrices = reprocessPrices;
		this.reprocessStatus = reprocessStatus;
		this.followUp = followUp;
		this.flushHeld = flushHeld;
		this.foldUsedListings = foldUsedListings;
		this.healthTick = healthTick;
		this.probe = probe;
		this.report = report;
	}

	/** 플러시·건강 없는 테스트 seam(기존 호출부 호환) — 발급·플러시·건강을 no-op/0으로 둔다. */
	PipelineScheduler(Runnable expireObservations, Supplier<IngestReport> ingest, Runnable preserveConditions,
			Supplier<List<Long>> reprocessPrices, Supplier<List<Long>> reprocessStatus,
			BiFunction<List<Long>, FollowUpKind, Integer> followUp, Supplier<PipelineSnapshot> probe,
			Consumer<PipelineTickReport> report) {
		this(expireObservations, () -> 0, ingest, preserveConditions, reprocessPrices, reprocessStatus, followUp,
				FlushReport::empty, FoldReport::empty, healthy -> { }, probe, report);
	}

	@Scheduled(fixedDelayString = "${core.pipeline.interval-ms:60000}",
			initialDelayString = "${core.pipeline.interval-ms:60000}")
	public void tick() {
		stepFailures = 0; // 이 틱의 단계 실패만 센다(틱은 겹치지 않는다)
		PipelineSnapshot before = snapshot();
		runStep("expire-observations", expireObservations);
		// 성적표 발급(PUR-04) — 만료 직후에 온다(REPORT_PENDING을 드레인). 발급은 quiet(관통 알림 없음).
		// 발급 수를 붙잡아 리포트에 싣는다 — 안 그러면 REPORT_PENDING 델타가 "만료 − 발급"으로 오염돼
		// purchasesExpired가 음수가 될 수 있다("카운터는 오염되지 않는 쪽을 센다").
		int reportCardsIssued = runStepReturning("issue-report-cards", issueReportCards, 0);
		IngestReport ingestReport = runStepReturning("ingest", ingest, IngestReport.empty());
		runStep("preserve-conditions", preserveConditions);
		List<Long> priceChanged = runStepReturning("reprocess-prices", reprocessPrices, List.of());
		List<Long> ended = runStepReturning("reprocess-status", reprocessStatus, List.of());
		// 후속 알림(AL-03) — 가격변화·종료한 딜 중 첫 알림이 나갔던 것에만. 종료가 마지막이라 닫히기 직전 값까지 반영.
		// 발송 수를 붙잡아 틱 리포트에 싣는다 — 안 그러면 sendFollowUps가 낸 값이 조용히 버려진다(Q-57 절반 카운터).
		int followUpPrice = runStepReturning("follow-up-price",
				() -> followUp.apply(priceChanged, FollowUpKind.PRICE_CHANGED), 0);
		int followUpEnded = runStepReturning("follow-up-ended",
				() -> followUp.apply(ended, FollowUpKind.ENDED), 0);
		// VERIFIED 후속(Q-13) — 이번 틱에 두 번째 이상 사이트로 흡수(병합)된 딜. ingest가 그 흡수 자체를
		// 첫 알림으로 다시 보내지 않는 대신(중복 발송 방지), 여기서 "교차검증됨" 후속으로만 알린다.
		// sendFollowUps의 멱등("첫 알림 나간 딜에만·종류당 1회")이 중복 병합에도 안전하게 만든다.
		int followUpVerified = runStepReturning("follow-up-verified",
				() -> followUp.apply(ingestReport.mergedDealIds(), FollowUpKind.VERIFIED), 0);
		// 방해금지가 끝난 보류분을 재평가해 보낸다(Q-20 ②) — 밤새 바뀐 상황을 발송 시점에 다시 판정한다.
		FlushReport flush = runStepReturning("flush-held", flushHeld, FlushReport.empty());
		// 중고 목록 접기는 신품 경로와 독립이다 — 순서 계약이 없어 끝에 둔다(한 단계 실패는 runStep이 격리).
		FoldReport usedFold = runStepReturning("fold-used-listings", foldUsedListings, FoldReport.empty());
		PipelineSnapshot after = snapshot();
		// 건강 = 스냅샷 왕복 + 단계 실패 0. DB가 닿지 않으면(스냅샷 null) 리포트는 못 내도 건강 신호는 낸다 —
		// 그래야 지속 DB 장애가 관리 알림으로 이어진다(OBS-03). 리포트는 스냅샷이 있을 때만.
		boolean healthy = before != null && after != null && stepFailures == 0;
		if (before != null && after != null) {
			// 단계가 터졌어도 보고한다 — 무엇이 처리됐고 무엇이 남았는지가 그때 더 중요하다. stepsFailed로 그 사실도 싣는다.
			report.accept(PipelineTickReport.between(before, after, ingestReport, reportCardsIssued, followUpPrice,
					followUpEnded, followUpVerified, stepFailures, flush.flushed(), flush.dropped(), usedFold));
		}
		healthTick.accept(healthy); // 연속 실패면 관리 알림(PipelineHealthMonitor)
	}

	/**
	 * 스냅샷 조회도 DB를 탄다. {@code runStep} 밖에 두면 DB 단절 시 틱 전체가 예외로 끝나 <b>단계는
	 * 한 번도 시도되지 않고</b>, 그 예외를 삼키는 것은 Spring이라 우리 로그에는 아무 흔적도 없다.
	 *
	 * @return 못 읽었으면 {@code null} — 부재는 부재로 표현한다(0으로 채운 리포트는 "아무 일도 없었다"는
	 *     거짓말이 된다). 해석은 {@link #tick()} 한 곳에 가둔다.
	 */
	private PipelineSnapshot snapshot() {
		try {
			return probe.get();
		}
		catch (RuntimeException failure) {
			log.error("pipeline snapshot failed", failure);
			return null;
		}
	}

	/**
	 * 한 단계의 실패가 다른 단계와 다음 주기를 죽이지 않게 격리한다. 다만 <b>뭉개지 않는다</b> —
	 * 단계 이름과 함께 남긴다. 관리 알림 채널(OBS-03)은 텔레그램 토큰 대기 중이라 지금은 로그가 전부다
	 * (docs/91 Q-56). 예외를 밖으로 내면 Spring이 삼켜 로그만 남기는 건 같지만, 뒤 단계가
	 * 실행되지 않는다.
	 */
	private void runStep(String name, Runnable step) {
		try {
			step.run();
		}
		catch (RuntimeException failure) {
			stepFailures++;
			log.error("pipeline step failed: {}", name, failure);
		}
	}

	/**
	 * {@link #runStep}의 반환값 버전 — 단계가 낸 값(전이 딜 id, 수집 리포트)을 뒤 단계·보고로 넘긴다.
	 * 실패는 격리하고 {@code onFailure}(중립값: 빈 목록·빈 리포트)로 떨어진다 — 그래야 한 단계가 터져도
	 * 나머지 틱과 보고가 산다.
	 */
	private <T> T runStepReturning(String name, Supplier<T> step, T onFailure) {
		try {
			return step.get();
		}
		catch (RuntimeException failure) {
			stepFailures++;
			log.error("pipeline step failed: {}", name, failure);
			return onFailure;
		}
	}
}
