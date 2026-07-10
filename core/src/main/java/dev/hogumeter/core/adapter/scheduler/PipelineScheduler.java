package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
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

import dev.hogumeter.core.application.IngestDealsUseCase;
import dev.hogumeter.core.application.ReprocessDealStatusUseCase;

/**
 * 파이프라인 트리거 — {@code raw_deal_post}를 주기적으로 소비한다.
 *
 * <p><b>이게 없으면 시스템은 죽어 있다.</b> collector가 원문을 아무리 적재해도
 * {@link IngestDealsUseCase#ingestPending()}을 부르는 사람이 없으면 {@code deal_event}가 생기지 않고,
 * 기준가 표본은 영원히 0이며 알림도 오지 않는다(2026-07-10까지 실제로 그랬다 — docs/91 Q-27 ⑤).
 *
 * <p>순서는 <b>ingest → reprocess</b>다. 이번 주기에 새로 링크된 원문까지 종료 판정이 보게 된다.
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
	private final Runnable reprocess;
	private final Supplier<PipelineSnapshot> probe;
	private final Consumer<PipelineTickReport> report;

	@Autowired
	PipelineScheduler(IngestDealsUseCase ingest, ReprocessDealStatusUseCase reprocess,
			RawDealPostRepository rawPosts, DealEventRepository dealEvents,
			DealEventSourceRepository sources, ReviewQueueItemRepository reviewQueue) {
		this(ingest::ingestPending, reprocess::reprocessEndedDeals,
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
	PipelineScheduler(Runnable ingest, Runnable reprocess, Supplier<PipelineSnapshot> probe,
			Consumer<PipelineTickReport> report) {
		this.ingest = ingest;
		this.reprocess = reprocess;
		this.probe = probe;
		this.report = report;
	}

	@Scheduled(fixedDelayString = "${core.pipeline.interval-ms:60000}",
			initialDelayString = "${core.pipeline.interval-ms:60000}")
	public void tick() {
		PipelineSnapshot before = probe.get();
		runStep("ingest", ingest);
		runStep("reprocess", reprocess);
		// 단계가 터졌어도 보고한다 — 무엇이 처리됐고 무엇이 남았는지가 그때 더 중요하다.
		report.accept(PipelineTickReport.between(before, probe.get()));
	}

	/**
	 * 한 단계의 실패가 다른 단계와 다음 주기를 죽이지 않게 격리한다. 다만 <b>뭉개지 않는다</b> —
	 * 단계 이름과 함께 남긴다. 관리 알림 채널(OBS-03)은 텔레그램 토큰 대기 중이라 지금은 로그가 전부다
	 * (docs/91 Q-56). 예외를 밖으로 내면 Spring이 삼켜 로그만 남기는 건 같지만, 두 번째 단계가
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
