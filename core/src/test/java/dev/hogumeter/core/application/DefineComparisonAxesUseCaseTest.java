package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisEntity;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-05 AC-17 ① — 비교축 정의는 <b>추가 전용</b>이다. 기존 축을 지우면 이미 승격된
 * {@code listing_axis_value}가 FK로 물려 있어 삭제가 위험하다 — 되돌리기 쉬운 쪽(추가만)을 택했다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DefineComparisonAxesUseCaseTest {

	@Autowired
	DefineComparisonAxesUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	ComparisonAxisRepository axes;

	private Long productId;

	@org.junit.jupiter.api.BeforeEach
	void setUp() {
		productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void definesNewAxesForTheProduct() {
		useCase.ensure(productId, List.of("배터리%", "구성"));

		assertThat(axes.findByProductId(productId)).extracting(ComparisonAxisEntity::getName)
				.containsExactlyInAnyOrder("배터리%", "구성");
	}

	@Test
	void reDefiningTheSameNameDoesNotDuplicate() {
		useCase.ensure(productId, List.of("배터리%"));
		useCase.ensure(productId, List.of("배터리%", "구성"));

		assertThat(axes.findByProductId(productId)).hasSize(2); // 배터리%는 한 번만
	}

	@Test
	void definingNeverDeletesExistingAxes() {
		useCase.ensure(productId, List.of("배터리%", "구성"));

		useCase.ensure(productId, List.of("구성")); // "배터리%"를 안 줬다고 지우지 않는다

		assertThat(axes.findByProductId(productId)).extracting(ComparisonAxisEntity::getName)
				.containsExactlyInAnyOrder("배터리%", "구성");
	}
}
