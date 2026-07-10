package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.GetProductsUseCase;
import dev.hogumeter.core.application.RegisterProductCommand;
import dev.hogumeter.core.application.RegisterProductUseCase;
import dev.hogumeter.core.domain.alert.InvalidAlertPolicyException;
import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * REG-03 설정 REST. 확정본 §7의 web 최소 슬라이스는 "등록 + 후보선택 + variant/키워드/<b>목표가</b> 설정"인데
 * 목표가를 저장할 길이 없었다 — 즉 확정본 §107의 "OR [사용자 목표가 이하]" 트리거는 발화할 수 없었다.
 *
 * <p>클라이언트 검증은 방어가 아니라 편의다. curl로 직접 치는 경우를 여기서 못박는다(Q-49의 교훈).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AlertPolicyEndpointTest {

	private static final String PATH = "/api/v1/variants/{variantId}/alert-policy";

	@Autowired
	MockMvc mockMvc;
	@Autowired
	RegisterProductUseCase registerProduct;
	@Autowired
	GetProductsUseCase getProducts;

	private long variantId() {
		long productId = registerProduct.register(new RegisterProductCommand("아이폰 17", "phone",
				DemandAxisMode.GROUPED,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))),
				List.of("아이폰17")));
		return getProducts.variantsOf(productId).get(0).variantId();
	}

	/** 미설정을 404로 내면 화면이 "없는 것"과 "못 읽은 것"을 구분하지 못한다. 값의 부재는 null로 말한다. */
	@Test
	void unconfiguredVariantReportsItselfAsUnconfigured() throws Exception {
		mockMvc.perform(get(PATH, variantId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configured").value(false))
			.andExpect(jsonPath("$.targetPrice").doesNotExist())
			.andExpect(jsonPath("$.periodMonths").doesNotExist());
	}

	@Test
	void putThenGetRoundTrips() throws Exception {
		long variantId = variantId();

		mockMvc.perform(put(PATH, variantId).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"targetPrice":900000,"periodMonths":3,"quietHoursStart":23,"quietHoursEnd":8}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configured").value(true));

		mockMvc.perform(get(PATH, variantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.configured").value(true))
			.andExpect(jsonPath("$.targetPrice").value(900000))
			.andExpect(jsonPath("$.periodMonths").value(3))
			.andExpect(jsonPath("$.quietHoursStart").value(23))
			.andExpect(jsonPath("$.quietHoursEnd").value(8));
	}

	@Test
	void nonPositiveTargetPriceIsFourHundredNotFiveHundred() throws Exception {
		mockMvc.perform(put(PATH, variantId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"targetPrice":0,"periodMonths":6}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(InvalidAlertPolicyException.CODE));
	}

	/** DB CHECK(0~23)에 먼저 닿으면 500 + 진단 불가. 도메인이 400으로 거절한다. */
	@Test
	void quietHourOutOfRangeIsFourHundred() throws Exception {
		mockMvc.perform(put(PATH, variantId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"periodMonths":6,"quietHoursStart":24,"quietHoursEnd":8}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(InvalidAlertPolicyException.CODE));
	}

	@Test
	void missingPeriodMonthsIsFourHundredNotANullPointer() throws Exception {
		mockMvc.perform(put(PATH, variantId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"targetPrice":900000}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(InvalidAlertPolicyException.CODE));
	}

	/** 기간 P는 기준가와 같은 개념이라 같은 에러코드를 쓴다 — 코드를 늘리면 클라이언트 분기가 늘어난다. */
	@Test
	void nonPositivePeriodReusesTheBenchmarkPeriodCode() throws Exception {
		mockMvc.perform(put(PATH, variantId()).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"periodMonths":0}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(InvalidBenchmarkPeriodException.CODE));
	}

	@Test
	void unknownVariantIsFourOhFour() throws Exception {
		mockMvc.perform(put(PATH, 999_999L).contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"periodMonths":6}"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(VariantNotFoundException.CODE));

		mockMvc.perform(get(PATH, 999_999L)).andExpect(status().isNotFound());
	}
}
