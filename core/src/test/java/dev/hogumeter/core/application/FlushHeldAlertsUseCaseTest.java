package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.HeldAlertEntity;
import dev.hogumeter.core.adapter.persistence.HeldAlertRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-20 ②: 방해금지가 끝난 보류 알림을 재평가해 실제로 보낸다(유실 방지). 시계를 08:00에 고정하고 정책의
 * 방해금지 창을 케이스마다 달리해 "끝났다/아직이다"를 결정적으로 가른다(같은 시계, 다른 정책).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FlushHeldAlertsUseCaseTest {

	@Autowired
	FlushHeldAlertsUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	AlertPolicyRepository policies;
	@Autowired
	HeldAlertRepository heldAlerts;
	@Autowired
	RecordingAlertSender sender;

	private long variantId;
	private long dealId;
	private int seq;

	@BeforeEach
	void setUp() {
		sender.sent.clear();
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		variantId = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB"))).getId();
		dealId = insertCrossVerifiedDeal(890_000L, DealStatus.VERIFIED); // 단일 딜 → SPARSE GOOD 알림 대상
	}

	private long insertCrossVerifiedDeal(long price, DealStatus status) {
		Instant when = Instant.parse("2026-07-20T00:00:00Z"); // 고정 시계(07-21) 6개월 창 안
		int n = seq++;
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + n, "https://ppomppu.test/" + n, "제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + n, "https://ruliweb.test/" + n, "제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null, price, price, price, price,
				Origin.LIVE, true, OutlierFlag.NONE, false, status, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
		return deal.getId();
	}

	@Test
	void flushesAndSendsWhenQuietHoursHavePassed() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, 0, 6, 5, List.of())); // 방해금지 0~6시 (08:00은 지남)
		heldAlerts.save(new HeldAlertEntity(dealId, variantId));

		FlushHeldAlertsUseCase.FlushReport report = useCase.flush();

		assertThat(report.flushed()).as("방해금지가 끝나 재평가·발송").isEqualTo(1);
		assertThat(report.dropped()).isZero();
		assertThat(sender.sent).as("실제로 나갔다").hasSize(1);
		assertThat(heldAlerts.findAll()).as("처리 후 큐에서 내려간다").isEmpty();
	}

	@Test
	void leavesHeldWhileStillInQuietHours() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, 6, 10, 5, List.of())); // 방해금지 6~10시 (08:00은 안)
		heldAlerts.save(new HeldAlertEntity(dealId, variantId));

		FlushHeldAlertsUseCase.FlushReport report = useCase.flush();

		assertThat(report.flushed()).as("아직 방해금지라 발송 안 함").isZero();
		assertThat(sender.sent).isEmpty();
		assertThat(heldAlerts.findAll()).as("큐에 그대로 남아 다음 틱을 기다린다").hasSize(1);
	}

	/**
	 * 재평가에서 딜이 더는 자격이 없으면(여기선 밤새 <b>종료</b>됨 → Q-27③ 억제) 지어낸 밤사이 값으로 알리지
	 * 않고 드롭한다(AL-07). ENDED "(종료됨)" 통지는 v1 범위 밖(docs/91 Q-20 ②) — 후속 알림 경로가 채운다.
	 */
	@Test
	void dropsWhenTheHeldDealNoLongerQualifies() {
		policies.save(new AlertPolicyEntity(variantId, null, 6, 0, 6, 5, List.of())); // 방해금지 지남
		long endedDeal = insertCrossVerifiedDeal(890_000L, DealStatus.ENDED); // 밤새 종료됨
		heldAlerts.save(new HeldAlertEntity(endedDeal, variantId));

		FlushHeldAlertsUseCase.FlushReport report = useCase.flush();

		assertThat(report.flushed()).isZero();
		assertThat(report.dropped()).as("종료된 딜은 재평가에서 억제 → 드롭").isEqualTo(1);
		assertThat(sender.sent).isEmpty();
		assertThat(heldAlerts.findAll()).isEmpty();
	}

	@TestConfiguration
	static class Config {
		/** 시계를 08:00에 고정 — 방해금지 창을 정책으로 달리해 "지남/아직"을 가른다. */
		@Bean
		@Primary
		Clock testClock() {
			return Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), ZoneOffset.UTC);
		}

		@Bean
		@Primary
		RecordingAlertSender recordingAlertSender() {
			return new RecordingAlertSender();
		}
	}

	static class RecordingAlertSender implements AlertSender {
		final List<AlertMessage> sent = new ArrayList<>();

		@Override
		public void send(AlertMessage message) {
			sent.add(message);
		}
	}
}
