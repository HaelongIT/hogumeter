package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.DealAlertEntity;
import dev.hogumeter.core.adapter.persistence.DealAlertRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
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
 * AL-03 후속 알림 발송 배선(Q-67). 순수 자격은 {@link dev.hogumeter.core.domain.alert.FollowUpEvaluator}가
 * 검증하므로 여기선 <b>주입</b>을 시험한다 — 첫 알림이 나간 딜에만 후속, 종류별 1회(멱등), 첫 알림 없는 딜·
 * 사라진 딜은 조용히 건너뛴다. 발송은 스파이로 통관 검증(실전송은 Q-20 뒤).
 */
@Import({ TestcontainersConfiguration.class, FollowUpAlertUseCaseTest.RecordingSenderConfig.class })
@SpringBootTest
@Transactional
class FollowUpAlertUseCaseTest {

	private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	FollowUpAlertUseCase followUp;
	@Autowired
	IngestDealsUseCase ingest;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	AliasRepository aliases;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealAlertRepository dealAlerts;
	@Autowired
	RecordingAlertSender recordingAlertSender;

	private long variantId;
	private int postSeq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		aliases.save(new AliasEntity(product.getId(), "아이폰17"));
		variantId = variant.getId();
		recordingAlertSender.sent.clear(); // 스파이는 싱글톤 — @Transactional이 롤백하지 않으므로 매 케이스 초기화
	}

	/** 첫 알림이 실제로 나간 딜을 만든다(딜 1건 → SPARSE GOOD 알림). @return 그 딜 id. */
	private long dealThatAlreadyAlerted() {
		rawPosts.save(new RawDealPost("ppomppu", "post" + postSeq++, "https://ppomppu.test/" + postSeq,
				"아이폰 17 256기가 특가 89만", 890_000L, T, T, "ACTIVE"));
		ingest.ingestPending();
		long dealId = dealEvents.findByVariantId(variantId).get(0).getId();
		assertThat(dealAlerts.existsByDealEventIdAndKind(dealId, DealAlertEntity.FIRST)).isTrue();
		recordingAlertSender.sent.clear(); // 첫 알림은 셌으니 비운다 — 이제 후속만 본다
		return dealId;
	}

	@Test
	void sendsFollowUpToDealThatAlreadyAlerted() {
		long dealId = dealThatAlreadyAlerted();

		int sent = followUp.sendFollowUps(List.of(dealId), FollowUpKind.PRICE_CHANGED);

		assertThat(sent).isEqualTo(1);
		assertThat(recordingAlertSender.sent).hasSize(1);
		assertThat(recordingAlertSender.sent.get(0).followUpKind()).isEqualTo(FollowUpKind.PRICE_CHANGED);
		assertThat(dealAlerts.findAll())
				.extracting(DealAlertEntity::getDealEventId, DealAlertEntity::getKind)
				.contains(tuple(dealId, FollowUpKind.PRICE_CHANGED.name()));
	}

	@Test
	void doesNotResendSameKind() {
		long dealId = dealThatAlreadyAlerted();
		followUp.sendFollowUps(List.of(dealId), FollowUpKind.PRICE_CHANGED);
		recordingAlertSender.sent.clear();

		int again = followUp.sendFollowUps(List.of(dealId), FollowUpKind.PRICE_CHANGED);

		assertThat(again).isZero(); // (deal_event_id, kind) unique — 매 틱 도는 후속이 재발송하지 않는다
		assertThat(recordingAlertSender.sent).isEmpty();
	}

	@Test
	void skipsDealWithoutFirstAlert() {
		// 최초 수집 시 이미 품절 → 딜 ENDED로 태어나고 첫 알림이 없다(Q-27③). 전이해도 후속을 만들지 않는다.
		rawPosts.save(new RawDealPost("ppomppu", "post" + postSeq++, "https://ppomppu.test/sold",
				"아이폰 17 256기가 특가 89만", 890_000L, T, T, "SOLD_OUT"));
		ingest.ingestPending();
		DealEventEntity deal = dealEvents.findByVariantId(variantId).get(0);
		assertThat(deal.getStatus()).isEqualTo(DealStatus.ENDED);
		recordingAlertSender.sent.clear();

		int sent = followUp.sendFollowUps(List.of(deal.getId()), FollowUpKind.ENDED);

		assertThat(sent).isZero();
		assertThat(recordingAlertSender.sent).isEmpty();
		assertThat(dealAlerts.existsByDealEventIdAndKind(deal.getId(), FollowUpKind.ENDED.name())).isFalse();
	}

	@Test
	void skipsMissingDeal() {
		int sent = followUp.sendFollowUps(List.of(999_999L), FollowUpKind.PRICE_CHANGED);

		assertThat(sent).isZero();
		assertThat(recordingAlertSender.sent).isEmpty();
	}

	@TestConfiguration
	static class RecordingSenderConfig {
		@Bean
		@Primary
		RecordingAlertSender recordingAlertSender() {
			return new RecordingAlertSender();
		}
	}

	/** 발송 대신 기록하는 스파이 — 후속이 나갔는가·무슨 종류인가를 통관 검증(@Primary로 StubAlertSender 대체). */
	static class RecordingAlertSender implements AlertSender {
		final List<AlertMessage> sent = new ArrayList<>();

		@Override
		public void send(AlertMessage message) {
			sent.add(message);
		}
	}
}
