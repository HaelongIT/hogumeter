package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** USED-01 등록 HTTP 경로 — POST /api/v1/products/{id}/used-searches → 201 + 실제 저장(Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsedSearchControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	UsedSearchBonusGroupRepository bonusGroups;

	@Test
	void postCreatesUsedSearchAndReturnsId() throws Exception {
		long productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		String body = """
				{
				  "required": ["아이폰17", "256"],
				  "bonusGroups": [{"keywords": ["S급", "민트"], "mode": "SORT"}],
				  "exclude": ["부품용"],
				  "targetPrice": 850000,
				  "pollIntervalMin": 10
				}
				""";

		mockMvc.perform(post("/api/v1/products/{productId}/used-searches", productId)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usedSearchId").exists());

		List<UsedSearchEntity> saved = searches.findByProductId(productId);
		assertThat(saved).hasSize(1);
		assertThat(saved.get(0).getRequiredKeywords()).containsExactly("아이폰17", "256");
		assertThat(bonusGroups.findByUsedSearchId(saved.get(0).getId())).hasSize(1);
	}
}
