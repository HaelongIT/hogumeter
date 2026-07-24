package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.ComputeDigestTransitionUseCase.DigestTransition;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.signal.SignalColor;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 ② 전환 배선. 딜이 없는 variant는 신호가 GRAY다 — 저장 색을 무엇으로 두느냐로 전환 판정을
 * 시험한다(현재 색 계산 자체는 SIG 테스트가 이미 잠갔다). {@link dev.hogumeter.core.domain.digest.DigestRules}의
 * 순수 규칙(색만 비교)이 실제 저장물·현재 신호와 배선됐는지가 여기 계약이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ComputeDigestTransitionUseCaseTest {

	@Autowired
	ComputeDigestTransitionUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DigestStateRepository digestStates;

	private long variantId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("전환 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of()));
		variantId = variant.getId();
		// 딜이 없으므로 현재 신호는 GRAY(SPARSE/NONE) — 저장 색만 바꿔가며 전환을 시험한다.
	}

	private void store(String color) {
		digestStates.save(new DigestStateEntity(variantId, Instant.parse("2026-07-17T20:00:00Z"), color,
				"NO_ACTIVE_DEAL", "GROUPED"));
	}

	@Test
	void firstDigestIsNotATransition() {
		// 저장물 없음 → 비교할 이전 값이 없다. 기준선일 뿐 전환이 아니다.
		DigestTransition t = useCase.transition(variantId);

		assertThat(t.from()).isNull();
		assertThat(t.to()).isEqualTo(SignalColor.GRAY);
		assertThat(t.reportable()).isFalse();
	}

	@Test
	void colorChangeIsAReportableTransition() {
		store("GREEN"); // 지난주 GREEN → 이번주 GRAY

		DigestTransition t = useCase.transition(variantId);

		assertThat(t.from()).isEqualTo(SignalColor.GREEN);
		assertThat(t.to()).isEqualTo(SignalColor.GRAY);
		assertThat(t.reportable()).isTrue();
	}

	@Test
	void sameColorIsSuppressed() {
		store("GRAY"); // 지난주도 GRAY → 색 불변, 억제

		DigestTransition t = useCase.transition(variantId);

		assertThat(t.from()).isEqualTo(SignalColor.GRAY);
		assertThat(t.reportable()).isFalse();
	}

	@Test
	void corruptStoredColorIsTreatedAsNoBaseline() {
		store("NOT_A_COLOR"); // 손상된 값 → 예외로 막지 않고 기준선으로 되돌린다

		DigestTransition t = useCase.transition(variantId);

		assertThat(t.from()).isNull();
		assertThat(t.reportable()).isFalse();
	}
}
