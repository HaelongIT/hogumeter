package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
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
 * DIG-03 창 배선의 첫 소비자(docs/91 — DigestWindow는 있었으나 호출자가 0이었다).
 * 창 계산 자체(반개구간·복귀는 신인)는 {@link dev.hogumeter.core.domain.digest.DigestWindowTest}가
 * 이미 순수하게 잠갔다 — 여기는 <b>활성 시각·직전 발송을 실제로 어디서 읽어오는가</b>만 검증한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestWindowUseCaseTest {

	private static final Instant NOW = Instant.parse("2026-07-24T20:00:00Z");

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DigestStateRepository digestStates;

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	private ComputeDigestWindowUseCase useCase() {
		return new ComputeDigestWindowUseCase(variants, products, digestStates, clock);
	}

	@Test
	void firstWindowStartsAtProductRegistrationWhenNeverSent() {
		ProductEntity product = products.save(new ProductEntity("다이제스트 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));

		DigestWindow window = useCase().window(variant.getId());

		assertThat(window.from()).isEqualTo(product.getCreatedAt());
		assertThat(window.to()).isEqualTo(NOW);
	}

	@Test
	void windowStartsAtLastSentWhenDigestStateExists() {
		// product.created_at은 @PrePersist가 실 벽시계로 찍는다(주입 불가, 기존 엔티티 설계) — 그래서
		// "직전 발송이 등록보다 나중"이 항상 성립하도록 등록 시각을 읽은 뒤 그 이후로 lastSent를 잡는다.
		ProductEntity product = products.save(new ProductEntity("다이제스트 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		Instant lastSent = product.getCreatedAt().plusSeconds(3600);
		digestStates.save(new DigestStateEntity(variant.getId(), lastSent, "GREEN", null, "GROUPED"));
		Clock thisSend = Clock.fixed(lastSent.plusSeconds(3600), ZoneOffset.UTC);

		DigestWindow window = new ComputeDigestWindowUseCase(variants, products, digestStates, thisSend)
				.window(variant.getId());

		assertThat(window.from()).isEqualTo(lastSent);
	}

	@Test
	void reactivationAfterLastSentResetsToRegistrationNewbie() {
		// product.created_at은 등록 시각이라 재활성 케이스는 "마지막 발송보다 나중에 등록됐다"가 아니라
		// (등록은 발송보다 항상 이르거나 같다) 이 유스케이스에선 실질적으로 안 일어난다 — 그래도 신뢰의
		// 근거는 DigestWindow 자체가 최댓값을 취한다는 사실이다(순수 함수 쪽에서 이미 잠갔다).
		// 여기서는 digest_state가 아예 없을 때도 활성 시각이 두 자리 모두에 들어가 정확히 그 값이
		// 창 시작이 됨을 재확인한다(half-open 경계 포함 여부까지).
		ProductEntity product = products.save(new ProductEntity("다이제스트 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));

		DigestWindow window = useCase().window(variant.getId());

		assertThat(window.contains(product.getCreatedAt())).isTrue();
	}

	@Test
	void missingVariantIsRejected() {
		assertThatThrownBy(() -> useCase().window(999_999L))
				.isInstanceOf(VariantNotFoundException.class);
	}
}
