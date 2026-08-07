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

/** Q-91(docs/91) 제품 수동 보관 손잡이 — 취소 가능(unarchive로 되돌리면 목록에 복귀). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SetProductArchivedUseCaseTest {

	@Autowired
	SetProductArchivedUseCase setArchived;
	@Autowired
	ProductRepository products;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void archivesAProduct() {
		long id = product("보관A");

		setArchived.archive(id);

		assertThat(products.findById(id).orElseThrow().isArchived()).isTrue();
	}

	@Test
	void canBeUnarchivedBackToVisible() {
		long id = product("보관B");
		setArchived.archive(id);

		setArchived.unarchive(id);

		assertThat(products.findById(id).orElseThrow().isArchived()).isFalse();
	}

	@Test
	void unknownProductIsRejected() {
		assertThatThrownBy(() -> setArchived.archive(999_999_999L))
				.isInstanceOf(ProductNotFoundException.class);
	}
}
