package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** PRI ②축소(docs/19) 수동 완료 손잡이 — 취소 가능(false로 되돌리면 대기열에 복귀). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SetProductManuallyCompletedUseCaseTest {

	@Autowired
	SetProductManuallyCompletedUseCase setManuallyCompleted;
	@Autowired
	ProductRepository products;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void marksAProductAsManuallyCompleted() {
		long id = product("수완A");

		setManuallyCompleted.set(id, true);

		assertThat(products.findById(id).orElseThrow().isManuallyCompleted()).isTrue();
	}

	@Test
	void canBeCancelledBackToFalse() {
		long id = product("수완B");
		setManuallyCompleted.set(id, true);

		setManuallyCompleted.set(id, false);

		assertThat(products.findById(id).orElseThrow().isManuallyCompleted()).isFalse();
	}

	@Test
	void unknownProductIsRejected() {
		assertThatThrownBy(() -> setManuallyCompleted.set(999_999_999L, true))
				.isInstanceOf(ProductNotFoundException.class);
	}
}
