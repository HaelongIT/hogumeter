package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PRI ②축소(docs/19) HTTP 경로 — 정렬 조회 + 순번·수동 완료 손잡이가 실제로 저장까지 닿는지. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PriorityControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void getPrioritizedListsWaitingProductFirst() throws Exception {
		long id = product("PRI 컨트롤러 테스트");

		mockMvc.perform(get("/api/v1/products/prioritized"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.productId == " + id + ")].waiting").value(true));
	}

	@Test
	void putPrioritySetsTheRankAndPersists() throws Exception {
		long id = product("PRI 순번 테스트");

		mockMvc.perform(put("/api/v1/products/" + id + "/priority").contentType(MediaType.APPLICATION_JSON)
				.content("{\"rank\": 3}"))
				.andExpect(status().isNoContent());

		assertThat(products.findById(id).orElseThrow().getPriorityRank()).isEqualTo(3);
	}

	@Test
	void putPriorityOnUnknownProductReturns404WithDomainCode() throws Exception {
		mockMvc.perform(put("/api/v1/products/999999999/priority").contentType(MediaType.APPLICATION_JSON)
				.content("{\"rank\": 1}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PRI_PRODUCT_NOT_FOUND"));
	}

	@Test
	void putManuallyCompletedPersists() throws Exception {
		long id = product("PRI 수동완료 테스트");

		mockMvc.perform(put("/api/v1/products/" + id + "/manually-completed").contentType(MediaType.APPLICATION_JSON)
				.content("{\"manuallyCompleted\": true}"))
				.andExpect(status().isNoContent());

		assertThat(products.findById(id).orElseThrow().isManuallyCompleted()).isTrue();
	}
}
