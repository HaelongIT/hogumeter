package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.application.IngestDealsUseCase;
import dev.hogumeter.core.application.ReprocessDealPricesUseCase;
import dev.hogumeter.core.application.ReprocessDealStatusUseCase;
import dev.hogumeter.core.domain.deal.DealStatus;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 파이프라인 트리거 — {@code raw_deal_post}를 주기적으로 소비한다.
 *
 * <p><b>이게 없으면 시스템은 죽어 있다.</b> collector가 원문을 아무리 적재해도
 * {@link IngestDealsUseCase#ingestPending()}을 부르는 사람이 없으면 {@code deal_event}가 생기지 않고,
 * 기준가 표본은 영원히 0이며 알림도 오지 않는다(2026-07-10까지 실제로 그랬다 — docs/91 Q-27 ⑤).
 *
 * <p>순서는 <b>ingest → 가격 → 종료</b>다.
 * <ol>
 * <li>ingest: 새 원문을 딜로 만든다. 이번 주기에 링크된 것까지 아래 두 단계가 보게 한다.</li>
 * <li>가격: 이미 링크된 원문의 새 가격을 딜에 반영한다(BM-01 AC-2, Q-27 ①).</li>
 * <li>종료: 링크된 원문이 전부 종료됐으면 딜을 ENDED로. <b>가격보다 뒤에 온다</b> — 종료된 딜의
 * 가격은 더 이상 갱신되지 않으므로, 종료 직전의 마지막 가격까지 반영하고 닫는다.</li>
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

	private final Runnable ingest;
	private final Runnable reprocessPrices;
	private final Runnable reprocessStatus;
	private final Supplier<PipelineSnapshot> probe;
	private final Consumer<PipelineTickReport> report;

	@Autowired
	PipelineScheduler(IngestDealsUseCase ingest, ReprocessDealPricesUseCase prices,
			ReprocessDealStatusUseCase status, RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, ReviewQueueItemRepository reviewQueue) {
		this(ingest::ingestPending, prices::reprocessPriceChanges, status::reprocessEndedDeals,
				() -> new PipelineSnapshot(
						rawPosts.count(),
						sources.count(),
						dealEvents.count(),
						reviewQueue.count(),
						dealEvents.findByStatusIn(List.of(DealStatus.ENDED)).size(),
						rawPosts.findUnprocessed().size()),
				tick -> log.info("pipeline tick {}", tick));
	}

	/** 테스트 seam — 이 프로젝트 테스트는 mock 대신 실객체·람다를 쓴다. */
	PipelineScheduler(Runnable ingest, Runnable reprocessPrices, Runnable reprocessStatus,
			Supplier<PipelineSnapshot> probe, Consumer<PipelineTickReport> report) {
		this.ingest = ingest;
		this.reprocessPrices = reprocessPrices;
		this.reprocessStatus = reprocessStatus;
		this.probe = probe;
		this.report = report;
	}

	@Scheduled(fixedDelayString = "${core.pipeline.interval-ms:60000}",
			initialDelayString = "${core.pipeline.interval-ms:60000}")
	public void tick() {
		PipelineSnapshot before = snapshot();
		runStep("ingest", ingest);
		runStep("reprocess-prices", reprocessPrices);
		runStep("reprocess-status", reprocessStatus);
		PipelineSnapshot after = snapshot();
		if (before == null || after == null) {
			return; // DB가 닿지 않는다. snapshot()이 이미 남겼고, 0으로 채운 리포트는 거짓말이다.
		}
		// 단계가 터졌어도 보고한다 — 무엇이 처리됐고 무엇이 남았는지가 그때 더 중요하다.
		report.accept(PipelineTickReport.between(before, after));
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
}
