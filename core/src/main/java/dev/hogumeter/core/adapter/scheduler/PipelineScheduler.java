package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.application.ExpirePurchaseObservationsUseCase;
import dev.hogumeter.core.application.FollowUpAlertUseCase;
import dev.hogumeter.core.application.IngestDealsUseCase;
import dev.hogumeter.core.application.IngestReport;
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
 * <p>순서는 <b>관찰만료 → ingest → 조건태그 → 가격 → 종료 → 후속알림</b>이다.
 * <ol>
 * <li>관찰만료: 관찰 기간이 끝난 구매를 REPORT_PENDING으로(PUR-01). <b>ingest보다 먼저</b> 온다 —
 * ingest는 새 딜마다 알림을 태우는데 PUR-03의 "산 뒤 알림"은 OBSERVING 관찰에만 발화한다.
 * 나중에 돌리면 이미 끝난 관찰이 이번 틱의 딜에 대해 한 번 더 알림을 낸다.</li>
 * <li>ingest: 새 원문을 딜로 만든다. 이번 주기에 링크된 것까지 아래 단계들이 보게 한다.</li>
 * <li>조건 태그: 원문의 조건부 가격 태그를 딜로 끌어올린다(BM-02 AC-2). <b>ingest 바로 뒤</b>에 온다 —
 * 방금 링크된 원문의 태그가 같은 틱에 보존된다. 이게 없어 표본의 약 1할이 무조건 가격 행세를 했다.</li>
 * <li>가격: 이미 링크된 원문의 새 가격을 딜에 반영한다(BM-01 AC-2, Q-27 ①).</li>
 * <li>종료: 링크된 원문이 전부 종료됐으면 딜을 ENDED로. <b>가격보다 뒤에 온다</b> — 종료된 딜의
 * 가격은 더 이상 갱신되지 않으므로, 종료 직전의 마지막 가격까지 반영하고 닫는다.</li>
 * <li>후속알림: 이번 틱에 가격변화·종료한 딜 중 <b>첫 알림이 나갔던 것</b>에만 후속을 보낸다(AL-03, Q-67).
 * 가격·종료 재처리가 낸 전이 id를 그대로 종류별로 흘려보낸다 — 종료가 마지막이라 닫히기 직전 값까지 반영된다.</li>
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
	private final Supplier<IngestReport> ingest;
	private final Runnable preserveConditions;
	private final Supplier<List<Long>> reprocessPrices;
	private final Supplier<List<Long>> reprocessStatus;
	private final BiFunction<List<Long>, FollowUpKind, Integer> followUp;
	private final Supplier<PipelineSnapshot> probe;
	private final Consumer<PipelineTickReport> report;

	@Autowired
	PipelineScheduler(ExpirePurchaseObservationsUseCase expireObservations, IngestDealsUseCase ingest,
			PreserveAppliedConditionsUseCase conditions, ReprocessDealPricesUseCase prices,
			ReprocessDealStatusUseCase status, FollowUpAlertUseCase followUp, RawDealPostRepository rawPosts,
			DealEventRepository dealEvents, DealEventSourceRepository sources,
			ReviewQueueItemRepository reviewQueue, PurchaseRepository purchases, JdbcTemplate jdbc) {
		this(expireObservations::expireDueObservations, ingest::ingestPending, conditions::preserveTags,
				prices::reprocessPriceChanges, status::reprocessEndedDeals, followUp::sendFollowUps,
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

	/** 테스트 seam — 이 프로젝트 테스트는 mock 대신 실객체·람다를 쓴다. */
	PipelineScheduler(Runnable expireObservations, Supplier<IngestReport> ingest, Runnable preserveConditions,
			Supplier<List<Long>> reprocessPrices, Supplier<List<Long>> reprocessStatus,
			BiFunction<List<Long>, FollowUpKind, Integer> followUp, Supplier<PipelineSnapshot> probe,
			Consumer<PipelineTickReport> report) {
		this.expireObservations = expireObservations;
		this.ingest = ingest;
		this.preserveConditions = preserveConditions;
		this.reprocessPrices = reprocessPrices;
		this.reprocessStatus = reprocessStatus;
		this.followUp = followUp;
		this.probe = probe;
		this.report = report;
	}

	@Scheduled(fixedDelayString = "${core.pipeline.interval-ms:60000}",
			initialDelayString = "${core.pipeline.interval-ms:60000}")
	public void tick() {
		PipelineSnapshot before = snapshot();
		runStep("expire-observations", expireObservations);
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
		PipelineSnapshot after = snapshot();
		if (before == null || after == null) {
			return; // DB가 닿지 않는다. snapshot()이 이미 남겼고, 0으로 채운 리포트는 거짓말이다.
		}
		// 단계가 터졌어도 보고한다 — 무엇이 처리됐고 무엇이 남았는지가 그때 더 중요하다.
		report.accept(PipelineTickReport.between(before, after, ingestReport, followUpPrice, followUpEnded));
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
			log.error("pipeline step failed: {}", name, failure);
			return onFailure;
		}
	}
}
