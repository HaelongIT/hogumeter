package dev.hogumeter.core.adapter.scheduler;

import dev.hogumeter.core.application.SendDigestUseCase;
import dev.hogumeter.core.application.SendDigestUseCase.DigestSendReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DIGEST 발송 스케줄(DIG-01) — <b>일요일 20시 KST 고정 상수로 먼저</b>(2026-07-25 결정, docs/91 Q-81:
 * 사용자 설정 손잡이는 되돌리기 쉬운 후속 seam으로 미룸). cron 표현식은 {@code zone}으로 KST를 명시해
 * 서버 타임존과 무관하게 항상 같은 실제 시각에 돈다.
 *
 * <p>{@link PipelineScheduler}와 같은 opt-out 관례 — {@code core.digest.enabled=false}로 끌 수 있고
 * (기본 true), 테스트 전역은 {@code core.digest.enabled=false}로 꺼서 스케줄이 테스트 도중 끼어들지
 * 않게 한다(등록 자체를 검증하는 {@code DigestSchedulerWiringTest}만 되켠다).
 *
 * <p>quiet hours 게이트는 아직 없다({@code SendDigestUseCase} javadoc 참조) — 전역 quiet hours 설정이
 * 없어서다. 스케줄이 20시 고정이라 당장의 위험은 낮다(문서화된 한계).
 */
@Component
@ConditionalOnProperty(name = "core.digest.enabled", havingValue = "true", matchIfMissing = true)
public class DigestScheduler {

	private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

	private final SendDigestUseCase sendDigest;

	@Autowired
	public DigestScheduler(SendDigestUseCase sendDigest) {
		this.sendDigest = sendDigest;
	}

	@Scheduled(cron = "0 0 20 * * SUN", zone = "Asia/Seoul")
	public void tick() {
		DigestSendReport report = sendDigest.send();
		log.info("[DIGEST] parts={} sent={} allSucceeded={}", report.parts(), report.sent(), report.allSucceeded());
	}
}
