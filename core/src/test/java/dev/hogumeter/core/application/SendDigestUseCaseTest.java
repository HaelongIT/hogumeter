package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.SendDigestUseCase.DigestSendReport;
import dev.hogumeter.core.application.port.out.DigestSender;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIGEST 발송 배선 — 분할(DIG-02)이 실제로 {@link DigestSender}를 여러 번 부르는지, 부분 실패가
 * {@code allSucceeded}에 정직하게 반영되는지, 그리고 <b>전 분할 성공 시에만</b> {@code digest_state}가
 * 갱신되는지(REL-03 원자성) 검증한다(분할 계산 자체는 {@code DigestSplitterTest}가 이미 순수하게 잠갔다).
 */
@Import({ TestcontainersConfiguration.class, SendDigestUseCaseTest.RecordingSenderConfig.class })
@SpringBootTest
@Transactional
class SendDigestUseCaseTest {

	@Autowired
	AssembleDigestUseCase assembler;
	@Autowired
	RenderDigestUseCase render;
	@Autowired
	RecordDigestSentUseCase recordSent;
	@Autowired
	IncrementDigestQueueAppearancesUseCase incrementQueueAppearances;
	@Autowired
	VariantRepository variants;
	@Autowired
	ProductRepository products;
	@Autowired
	GetPurchaseObservationsUseCase observations;
	@Autowired
	DigestStateRepository digestStates;
	@Autowired
	RecordingDigestSender recordingSender;
	@Autowired
	org.springframework.jdbc.core.JdbcTemplate jdbc;

	private SendDigestUseCase useCase(int maxLength) {
		return new SendDigestUseCase(assembler, render, recordingSender, recordSent, incrementQueueAppearances,
				variants, products, observations, maxLength);
	}

	private long enqueueReviewItem() {
		return jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status, created_at, last_seen_at)
				values ('UNCLASSIFIED', '{}'::jsonb, 'PENDING', now(), now()) returning id
				""", Long.class);
	}

	private int appearancesOf(long id) {
		return jdbc.queryForObject("select digest_appearances from review_queue_item where id = ?", Integer.class, id);
	}

	@Test
	void splitsIntoMultiplePartsAndSendsEachWhenAllSucceed() {
		recordingSender.results.clear();
		recordingSender.sent.clear();

		DigestSendReport report = useCase(20).send(); // 작은 한도로 분할 강제

		assertThat(report.parts()).isGreaterThan(1);
		assertThat(recordingSender.sent).hasSize(report.parts());
		assertThat(report.sent()).isEqualTo(report.parts());
		assertThat(report.allSucceeded()).isTrue();
	}

	@Test
	void oneFailedPartMakesTheWholeSendUnsuccessful() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		recordingSender.results.add(true);
		recordingSender.results.add(false); // 두 번째 조각만 실패

		DigestSendReport report = useCase(20).send();

		assertThat(report.parts()).isGreaterThan(1);
		assertThat(report.sent()).isLessThan(report.parts());
		assertThat(report.allSucceeded()).isFalse();
	}

	@Test
	void successfulSendRecordsDigestStateForEveryRegisteredVariant() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		ProductEntity product = products.save(new ProductEntity("발송 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));

		useCase(SendDigestUseCase.TELEGRAM_MAX_LENGTH).send();

		DigestStateEntity state = digestStates.findById(variant.getId()).orElseThrow();
		assertThat(state.getStoredColor()).isNotNull(); // 조립이 구한 현재 색을 그대로 기록
		assertThat(state.getStoredBasisMode()).isEqualTo("GROUPED");
		assertThat(state.getStoredContext()).isNull(); // 구매 없음 → 문맥 없음
		assertThat(state.getLastSentAt()).isNotNull();
	}

	@Test
	void failedSendDoesNotRecordDigestState() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		recordingSender.results.add(false); // 유일한 조각부터 실패
		ProductEntity product = products.save(new ProductEntity("발송 실패 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));

		useCase(SendDigestUseCase.TELEGRAM_MAX_LENGTH).send();

		assertThat(digestStates.findById(variant.getId())).isEmpty();
	}

	@Test
	void successfulSendIncrementsDigestAppearancesForEveryQueueItem() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		long itemId = enqueueReviewItem();

		useCase(SendDigestUseCase.TELEGRAM_MAX_LENGTH).send();

		assertThat(appearancesOf(itemId)).isEqualTo(1);
	}

	@Test
	void failedSendDoesNotIncrementDigestAppearances() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		recordingSender.results.add(false);
		long itemId = enqueueReviewItem();

		useCase(SendDigestUseCase.TELEGRAM_MAX_LENGTH).send();

		assertThat(appearancesOf(itemId)).isZero();
	}

	static class RecordingSenderConfig {
		@Bean
		@Primary
		RecordingDigestSender recordingDigestSender() {
			return new RecordingDigestSender();
		}
	}

	/** 성공/실패를 미리 정해 순서대로 돌려주는 스파이 — 기본은 항상 성공. */
	static class RecordingDigestSender implements DigestSender {
		final List<String> sent = new ArrayList<>();
		final Queue<Boolean> results = new LinkedBlockingQueue<>();

		@Override
		public boolean sendDigest(String text) {
			sent.add(text);
			Boolean next = results.poll();
			return next == null || next;
		}
	}
}
