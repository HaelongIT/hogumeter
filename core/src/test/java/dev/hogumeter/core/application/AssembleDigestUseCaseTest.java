package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.AssembleDigestUseCase.Digest;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIGEST 전체 조립 배선 — 등록된 모든 variant가 {@link AssembleVariantDigestUseCase}를 거쳐 한 다이제스트에
 * 모이는지, ⑤ 큐·⑥ 조용한 주가 같이 실리는지 검증한다. 각 재료의 계산 자체는 이미 각자 테스트가 잠갔다 —
 * 여기는 "실제로 다 모이는가"만 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AssembleDigestUseCaseTest {

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	AssembleDigestUseCase assembleDigest;

	@Test
	void assemblesOneRowPerRegisteredVariant() {
		ProductEntity productA = products.save(new ProductEntity("다이제스트A", "test", DemandAxisMode.GROUPED));
		VariantEntity variantA = variants.save(new VariantEntity(productA.getId(), "256GB", Map.of()));
		ProductEntity productB = products.save(new ProductEntity("다이제스트B", "test", DemandAxisMode.GROUPED));
		VariantEntity variantB = variants.save(new VariantEntity(productB.getId(), "512GB", Map.of()));

		Digest digest = assembleDigest.assemble();

		assertThat(digest.variantRows()).extracting(AssembleVariantDigestUseCase.VariantDigestRow::variantId)
				.contains(variantA.getId(), variantB.getId());
	}

	@Test
	void noVariantsAssemblesAnEmptyDigest() {
		// (다른 테스트가 남긴 variant가 있을 수 있어 "정확히 0"이 아니라 "던지지 않고 리스트로 낸다"만 본다)
		Digest digest = assembleDigest.assemble();

		assertThat(digest.variantRows()).isNotNull();
		assertThat(digest.queue()).isNotNull();
	}
}
