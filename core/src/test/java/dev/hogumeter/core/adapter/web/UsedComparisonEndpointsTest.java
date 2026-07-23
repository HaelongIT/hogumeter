package dev.hogumeter.core.adapter.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-05 REST 계약(AC-16·17·18) 관통 — 메모→축 정의→값 승격→비교표 조회를 하나의 흐름으로 검증한다.
 * 각 유스케이스 자체는 *UseCaseTest들이 이미 충분히 덮는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsedComparisonEndpointsTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	ListingRepository listings;

	private long productId;
	private long listingId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		productId = product.getId();
		long searchId = searches.save(new UsedSearchEntity(productId, "BUNJANG", List.of("아이폰17"),
				List.of(), null, 10)).getId();
		listingId = listings.save(new ListingEntity(searchId, "a1", "아이폰 17 256", 800_000L, Instant.now()))
				.getId();
	}

	@Test
	void noteDefineAxisPromoteAndCompareEndToEnd() throws Exception {
		mockMvc.perform(post("/api/v1/listings/{id}/notes", listingId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"body\": \"잔기스 있음\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.noteId").exists());

		String axisId = mockMvc.perform(put("/api/v1/products/{id}/comparison-axes", productId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"names\": [\"배터리%\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("배터리%"))
				.andReturn().getResponse().getContentAsString()
				.replaceAll(".*\"id\":(\\d+).*", "$1");

		mockMvc.perform(post("/api/v1/listings/{id}/axis-values", listingId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"axisId\": " + axisId + ", \"value\": \"92%\"}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/products/{id}/comparison", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.axes[0].name").value("배터리%"))
				.andExpect(jsonPath("$.rows[0].title").value("아이폰 17 256"))
				.andExpect(jsonPath("$.rows[0].axisValues.['" + axisId + "']").value("92%"))
				.andExpect(jsonPath("$.rows[0].notes", hasItem("잔기스 있음")));
	}

	@Test
	void redefiningAxesIsAdditiveNotDestructive() throws Exception {
		mockMvc.perform(put("/api/v1/products/{id}/comparison-axes", productId)
				.contentType(MediaType.APPLICATION_JSON).content("{\"names\": [\"배터리%\"]}"));

		mockMvc.perform(put("/api/v1/products/{id}/comparison-axes", productId)
						.contentType(MediaType.APPLICATION_JSON).content("{\"names\": [\"구성\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", hasItem("배터리%"))) // 지워지지 않았다
				.andExpect(jsonPath("$[*].name", hasItem("구성")));
	}

	@Test
	void promotingToUnknownAxisIs404() throws Exception {
		mockMvc.perform(post("/api/v1/listings/{id}/axis-values", listingId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"axisId\": 999999, \"value\": \"x\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("COMPARISON_AXIS_NOT_FOUND"));
	}
}
