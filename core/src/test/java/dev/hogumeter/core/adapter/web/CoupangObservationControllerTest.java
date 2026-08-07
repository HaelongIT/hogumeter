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
import org.springframework.test.annotation.DirtiesContext;
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

	/**
	 * BE-23(코드리뷰 20260806) — 순수 도메인({@code FixedWindowRateLimiterTest})은 잠겨 있었지만,
	 * 이 컨트롤러가 실제로 429 + RATE_LIMIT_EXCEEDED를 응답하는지는 아무 테스트도 확인하지 않았다
	 * (기존 테스트 5개가 요청 1회씩만 보내 레이트리밋 창을 못 채웠다). 레이트리밋(분당 30건)을
	 * 실제로 채워 배선을 관통 검증한다 — 다른 테스트가 같은 클래스 안에서 앞서 몇 건을 이미
	 * 소비했을 수 있으므로(창은 컨트롤러 빈 하나가 공유) 넉넉히 35회를 보내 429가 실제로 나오는지만
	 * 본다(정확히 몇 번째인지는 테스트 실행 순서에 의존하므로 확인하지 않는다).
	 *
	 * <p>{@code @DirtiesContext}: {@code FixedWindowRateLimiter}는 컨트롤러 빈 하나가 들고 있는
	 * 상태라 이 테스트가 분당 상한을 채우면 같은 컨텍스트를 공유하는 다른 테스트까지 오염된다 —
	 * 실측 확인(이 테스트가 먼저 돌면 형제 테스트가 429로 실패했다). 이 메서드 뒤에 컨텍스트를
	 * 새로 만들어 레이트리밋 상태를 리셋한다.
	 */
	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void rateLimitExceededReturns429WithCode() throws Exception {
		boolean saw429 = false;
		for (int i = 0; i < 35 && !saw429; i++) {
			int status = mockMvc.perform(post("/api/v1/coupang/observations")
							.header("X-Extension-Token", TOKEN)
							.contentType(MediaType.APPLICATION_JSON).content(body(1_000_000 + i)))
					.andReturn().getResponse().getStatus();
			if (status == 429) {
				saw429 = true;
			}
		}

		org.assertj.core.api.Assertions.assertThat(saw429)
				.as("분당 30건 상한을 넘기면 429가 나야 한다 — 순수 도메인이 아니라 컨트롤러 배선 자체를 본다")
				.isTrue();

		// 이미 상한을 넘긴 상태이므로 바로 다음 요청도 429 + 계약된 에러코드여야 한다.
		mockMvc.perform(post("/api/v1/coupang/observations")
						.header("X-Extension-Token", TOKEN)
						.contentType(MediaType.APPLICATION_JSON).content(body(999)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}
}
