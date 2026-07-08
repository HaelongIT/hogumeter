package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 1 등록 HTTP 경로 — POST /api/v1/products → 201 + 실제 저장(Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegistrationControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;

	@Test
	void postCreatesProductAndReturnsId() throws Exception {
		String body = """
				{
				  "name": "아이폰 17",
				  "category": "스마트폰",
				  "demandAxisMode": "SPLIT",
				  "axes": [{"axisType": "PRICE", "name": "용량", "allowedValues": ["256GB", "512GB"]}],
				  "variants": [{"label": "256GB", "priceAxisValues": {"용량": "256GB"}}],
				  "aliases": ["아이폰17"]
				}
				""";

		mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productId").isNumber());

		assertThat(products.count()).isEqualTo(1);
		assertThat(variants.count()).isEqualTo(1);
	}
}
