package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** CMP-02 REST 계약(SEC-04). 서버는 쿠팡에 접근하지 않는다 — 확장이 보낸 값을 받아 저장·조회할 뿐. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CoupangObservationControllerTest {

	private static final String TOKEN = "test-fixed-token"; // core/src/test/resources/application.properties

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;

	private long variantId;

	@BeforeEach
	void setUp() {
		Long productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
	}

	private String body(long price) {
		return "{\"variantId\": " + variantId + ", \"regularPrice\": " + price
				+ ", \"wowPrice\": null, \"shippingFee\": 0, \"url\": \"https://www.coupang.com/vp/products/1\"}";
	}

	@Test
	void validTokenIngestsAndLatestPriceReflectsIt() throws Exception {
		mockMvc.perform(post("/api/v1/coupang/observations")
						.header("X-Extension-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON).content(body(1_200_000)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.observationId").exists());

		mockMvc.perform(get("/api/v1/coupang/variants/{id}/latest-price", variantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regularPrice").value(1_200_000));
	}

	@Test
	void missingTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/coupang/observations")
						.contentType(MediaType.APPLICATION_JSON).content(body(1_200_000)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("EXTENSION_AUTH_FAILED"));
	}

	@Test
	void wrongTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/coupang/observations")
						.header("X-Extension-Token", "wrong-token")
						.contentType(MediaType.APPLICATION_JSON).content(body(1_200_000)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void noObservationYetReturnsAllNullsNotFabricatedZeros() throws Exception {
		mockMvc.perform(get("/api/v1/coupang/variants/{id}/latest-price", variantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.regularPrice").doesNotExist())
				.andExpect(jsonPath("$.wowPrice").doesNotExist());
	}

	@Test
	void invalidPriceIsRejectedNotStored() throws Exception {
		mockMvc.perform(post("/api/v1/coupang/observations")
						.header("X-Extension-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON).content(body(0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COUPANG_OBSERVATION"));
	}
}
