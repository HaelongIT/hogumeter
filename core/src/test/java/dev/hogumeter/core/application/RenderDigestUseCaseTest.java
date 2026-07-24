package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DigestStateEntity;
import dev.hogumeter.core.adapter.persistence.DigestStateRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIG-04 발송 문구 배선 — 등록된 variant의 실제 이름이 렌더링 결과에 실제로 실리는지 검증한다(순수
 * 포맷터는 이미 {@code DigestFormatterTest}가 잠갔다 — 여기는 "이름이 실제로 전달되는가"만 본다).
 *
 * <p>딜 없는 신규 variant는 무변동이라 개별 줄이 아니라 합산 줄로 빠진다(⑤ 큐와 무관, DIG-05) — 그러면
 * 이름이 실제로 전달됐는지 볼 수 없다. 그래서 <b>저장 색(GREEN)과 현재 색(딜 0건 → GRAY)을 어긋나게</b>
 * 만들어 ② 전환을 보고 대상으로 만든다 — 창(시계) 타이밍과 무관하게 신호를 확보하는 가장 단순한 방법이다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RenderDigestUseCaseTest {

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DigestStateRepository digestStates;
	@Autowired
	RenderDigestUseCase renderDigest;

	@Test
	void rendersRegisteredVariantsWithTheirRealNamesNotUnknown() {
		ProductEntity product = products.save(new ProductEntity("렌더 테스트", "test", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "1TB", Map.of()));
		digestStates.save(new DigestStateEntity(variant.getId(), product.getCreatedAt(), "GREEN", "NO_ACTIVE_DEAL",
				"GROUPED"));

		String out = renderDigest.render();

		assertThat(out).contains("렌더 테스트 1TB");
		assertThat(out).doesNotContain("대상 미상");
	}
}
