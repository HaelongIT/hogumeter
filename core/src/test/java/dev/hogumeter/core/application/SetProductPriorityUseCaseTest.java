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

/** PRI ②축소(docs/19) 순번 설정 — 유일 순번 위반은 도메인 예외로, 미지정(null)은 그대로 허용. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SetProductPriorityUseCaseTest {

	@Autowired
	SetProductPriorityUseCase setPriority;
	@Autowired
	ProductRepository products;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void assignsARankToAProduct() {
		long id = product("우선순위A");

		setPriority.setPriority(id, 1);

		assertThat(products.findById(id).orElseThrow().getPriorityRank()).isEqualTo(1);
	}

	@Test
	void nullClearsThePreviouslyAssignedRank() {
		long id = product("우선순위B");
		setPriority.setPriority(id, 1);

		setPriority.setPriority(id, null);

		assertThat(products.findById(id).orElseThrow().getPriorityRank()).isNull();
	}

	@Test
	void twoProductsCannotShareTheSameRank() {
		long first = product("우선순위C1");
		long second = product("우선순위C2");
		setPriority.setPriority(first, 5);

		assertThatThrownBy(() -> setPriority.setPriority(second, 5))
				.isInstanceOf(DuplicatePriorityRankException.class);
	}

	@Test
	void unknownProductIsRejected() {
		assertThatThrownBy(() -> setPriority.setPriority(999_999_999L, 1))
				.isInstanceOf(ProductNotFoundException.class);
	}
}
