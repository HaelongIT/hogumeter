package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Q-91(docs/91) HTTP 경로 — 보관/복원이 실제로 저장까지 닿는지. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void postArchiveMarksTheProductArchived() throws Exception {
		long id = product("Q-91 보관 테스트");

		mockMvc.perform(post("/api/v1/products/" + id + "/archive")).andExpect(status().isNoContent());

		assertThat(products.findById(id).orElseThrow().isArchived()).isTrue();
	}

	@Test
	void postUnarchiveRestoresVisibility() throws Exception {
		long id = product("Q-91 복원 테스트");
		mockMvc.perform(post("/api/v1/products/" + id + "/archive"));

		mockMvc.perform(post("/api/v1/products/" + id + "/unarchive")).andExpect(status().isNoContent());

		assertThat(products.findById(id).orElseThrow().isArchived()).isFalse();
	}

	@Test
	void archivingAnUnknownProductIs404() throws Exception {
		mockMvc.perform(post("/api/v1/products/999999999/archive")).andExpect(status().isNotFound());
	}
}
