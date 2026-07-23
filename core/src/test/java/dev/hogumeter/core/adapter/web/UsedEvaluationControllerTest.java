package dev.hogumeter.core.adapter.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
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

/** USED-04 평가기 REST 계약(AC-12·13). 유스케이스 배선은 EvaluateListingUseCaseTest가, 여기는 HTTP 표면만. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsedEvaluationControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;

	private long searchId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		searchId = searches.save(new UsedSearchEntity(product.getId(), "BUNJANG", List.of("아이폰17"),
				List.of("이민 급처"), 900_000L, 10)).getId();
	}

	@Test
	void manualInputReturns200WithStructuredListing() throws Exception {
		String body = """
				{"kind": "MANUAL", "title": "아이폰 17 256 S급", "price": 850000}""";

		mockMvc.perform(post("/api/v1/used-searches/{id}/evaluate", searchId)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.needsInput").doesNotExist())
				.andExpect(jsonPath("$.listing.title").value("아이폰 17 256 S급"))
				.andExpect(jsonPath("$.listing.price").value(850_000))
				.andExpect(jsonPath("$.priceContext.source").value("번개장터 활성 매물"))
				.andExpect(jsonPath("$.riskSignals").isArray());
	}

	@Test
	void textWithoutPriceAsksForManualFallback() throws Exception {
		String body = """
				{"kind": "TEXT", "text": "가격은 쪽지 주세요"}""";

		mockMvc.perform(post("/api/v1/used-searches/{id}/evaluate", searchId)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.needsInput").value("MANUAL"))
				.andExpect(jsonPath("$.listing").doesNotExist());
	}

	@Test
	void excludeKeywordHitIsListedAsRiskSignalNotAVerdict() throws Exception {
		String body = """
				{"kind": "MANUAL", "title": "아이폰 17 이민 급처 팝니다", "price": 700000}""";

		mockMvc.perform(post("/api/v1/used-searches/{id}/evaluate", searchId)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.riskSignals[*].detail", hasItem("이민 급처")));
	}

	@Test
	void unknownSearchIs404() throws Exception {
		String body = """
				{"kind": "MANUAL", "title": "x", "price": 1}""";

		mockMvc.perform(post("/api/v1/used-searches/{id}/evaluate", 999_999)
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USED_SEARCH_NOT_FOUND"));
	}
}
