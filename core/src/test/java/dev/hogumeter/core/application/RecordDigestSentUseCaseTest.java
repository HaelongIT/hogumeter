package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-02 저장물 쓰기의 첫 소비자 배선. {@link ComputeDigestWindowUseCase}와 짝을 이뤄 왕복을
 * 검증한다 — 기록하면 다음 창 계산이 그 시각부터 시작해야 한다(REL-03 원자성의 관찰 가능한 효과).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RecordDigestSentUseCaseTest {

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DigestStateRepository digestStates;

	@Test
	void recordingCreatesARowWhenNoneExisted() {
		ProductEntity product = products.save(new ProductEntity("다이제스트 쓰기 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		Clock clock = Clock.fixed(Instant.parse("2026-07-24T20:00:00Z"), ZoneOffset.UTC);

		new RecordDigestSentUseCase(digestStates, clock).recordSent(variant.getId(), "GREEN", "ACTIVE_DEAL", "GROUPED");

		DigestStateEntity saved = digestStates.findById(variant.getId()).orElseThrow();
		assertThat(saved.getLastSentAt()).isEqualTo(Instant.parse("2026-07-24T20:00:00Z"));
		assertThat(saved.getStoredColor()).isEqualTo("GREEN");
		assertThat(saved.getStoredContext()).isEqualTo("ACTIVE_DEAL");
		assertThat(saved.getStoredBasisMode()).isEqualTo("GROUPED");
	}

	@Test
	void recordingAgainUpdatesTheExistingRowRatherThanDuplicating() {
		ProductEntity product = products.save(new ProductEntity("다이제스트 쓰기 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		Clock first = Clock.fixed(Instant.parse("2026-07-17T20:00:00Z"), ZoneOffset.UTC);
		Clock second = Clock.fixed(Instant.parse("2026-07-24T20:00:00Z"), ZoneOffset.UTC);

		new RecordDigestSentUseCase(digestStates, first).recordSent(variant.getId(), "GRAY", "NO_ACTIVE_DEAL", "GROUPED");
		new RecordDigestSentUseCase(digestStates, second).recordSent(variant.getId(), "GREEN", "ACTIVE_DEAL", "GROUPED");

		assertThat(digestStates.count()).isEqualTo(1);
		DigestStateEntity saved = digestStates.findById(variant.getId()).orElseThrow();
		assertThat(saved.getLastSentAt()).isEqualTo(Instant.parse("2026-07-24T20:00:00Z"));
		assertThat(saved.getStoredColor()).isEqualTo("GREEN");
	}

	@Test
	void nextWindowStartsRightAfterARecordedSend() {
		// 왕복 증명: 기록 → 다음 창 계산이 그 발송 시각을 창 시작으로 쓴다.
		ProductEntity product = products.save(new ProductEntity("다이제스트 왕복 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		Instant sentAt = product.getCreatedAt().plusSeconds(3600);
		Clock sendClock = Clock.fixed(sentAt, ZoneOffset.UTC);
		new RecordDigestSentUseCase(digestStates, sendClock).recordSent(variant.getId(), "GREEN", "ACTIVE_DEAL", "GROUPED");

		Instant nextSend = sentAt.plusSeconds(7 * 24 * 3600);
		Clock nextClock = Clock.fixed(nextSend, ZoneOffset.UTC);
		DigestWindow window = new ComputeDigestWindowUseCase(variants, products, digestStates, nextClock)
				.window(variant.getId());

		assertThat(window.from()).isEqualTo(sentAt);
		assertThat(window.to()).isEqualTo(nextSend);
	}
}
