package dev.hogumeter.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.GetBenchmarkUseCase;
import dev.hogumeter.core.application.IngestDealsUseCase;
import dev.hogumeter.core.application.RegisterProductCommand;
import dev.hogumeter.core.application.RegisterProductUseCase;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * M1 신품 코어 루프 종단 스모크 — 등록 → 원문 주입 → 수집 파이프라인 → 기준가 조회 → 알림 판정
 * 한 흐름을 실 스키마(Testcontainers)로 검증. 발송은 기록 스텁으로 대체("무엇을 보냈나"만 확인).
 */
@Import({ TestcontainersConfiguration.class, NewGoodsPipelineIntegrationTest.RecordingConfig.class })
@SpringBootTest
@Transactional
class NewGoodsPipelineIntegrationTest {

	@TestConfiguration
	static class RecordingConfig {
		@Bean
		@Primary
		RecordingAlertSender recordingAlertSender() {
			return new RecordingAlertSender();
		}
	}

	static class RecordingAlertSender implements AlertSender {
		final List<AlertMessage> sent = new CopyOnWriteArrayList<>();

		@Override
		public void send(AlertMessage message) {
			sent.add(message);
		}
	}

	@Autowired
	RegisterProductUseCase register;
	@Autowired
	IngestDealsUseCase ingest;
	@Autowired
	GetBenchmarkUseCase getBenchmark;
	@Autowired
	VariantRepository variants;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	RecordingAlertSender alertSender;

	private int seq;

	@Test
	void registerToIngestToBenchmarkToAlert() {
		// 1) 등록 — 아이폰 17 / 256GB variant / 별칭
		long productId = register.register(new RegisterProductCommand("아이폰 17", "스마트폰", DemandAxisMode.SPLIT,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))),
				List.of("아이폰17")));
		long variantId = variants.findByProductId(productId).get(0).getId();

		// 2) 원문 주입 — 30k 간격 5건(별개 딜) + 892k(890k에 병합)
		savePost("ppomppu", 830_000);
		savePost("ppomppu", 860_000);
		savePost("ppomppu", 890_000);
		savePost("ppomppu", 920_000);
		savePost("ppomppu", 950_000);
		savePost("ruliweb", 892_000); // 890k와 병합 → 교차검증

		// 3) 수집 파이프라인 (매칭→병합→이상치→알림)
		ingest.ingestPending();

		// 4) 기준가 조회
		BenchmarkView view = getBenchmark.getBenchmark(variantId, 6, false);
		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.n()).isEqualTo(5); // 892k는 890k에 병합 → 딜 5건
		assertThat(view.m()).isEqualTo(1); // 병합된 890k만 교차검증
		assertThat(view.benchmarkPrice()).isEqualTo(890_000L); // median{830,860,890,920,950}k

		// 5) 알림 — 수집 중 기준가 이하 딜에 발송 판정이 났다(기록 스텁)
		assertThat(alertSender.sent).isNotEmpty();
	}

	private void savePost(String site, long price) {
		Instant when = Instant.now().minus(Duration.ofDays(10));
		rawPosts.save(new RawDealPost(site, "post" + seq++, "https://" + site + ".test/" + seq,
				"아이폰 17 256기가 자급제 특가", price, when, when, "ACTIVE"));
	}
}
