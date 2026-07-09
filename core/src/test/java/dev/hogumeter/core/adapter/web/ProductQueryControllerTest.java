package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.RegisterProductCommand;
import dev.hogumeter.core.application.RegisterProductUseCase;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록한 제품을 다시 찾아가는 읽기 전용 조회(REG). 이게 없으면 web은 POST 응답의 productId를
 * 잃는 순간 자기가 만든 variant로 돌아갈 수 없다 — 기준가·신호·주기 조회가 전부 variantId를 요구한다.
 *
 * <p>@Transactional로 tx 롤백 — @SpringBootTest는 컨테이너를 공유하고 커밋이 누수된다(docs/99).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductQueryControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	RegisterProductUseCase registerProduct;

	@Test
	void listsRegisteredProductsWithTheirVariants() throws Exception {
		long productId = registerProduct.register(iphone17());

		mockMvc.perform(get("/api/v1/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.productId == %d)].name".formatted(productId)).value("아이폰 17"))
				.andExpect(jsonPath("$[?(@.productId == %d)].category".formatted(productId)).value("phone"))
				.andExpect(jsonPath("$[?(@.productId == %d)].demandAxisMode".formatted(productId))
						.value("GROUPED"))
				.andExpect(jsonPath("$[?(@.productId == %d)].variants[*].label".formatted(productId))
						.value(org.hamcrest.Matchers.containsInAnyOrder("256GB", "512GB")));
	}

	@Test
	void exposesVariantIdsSoTheClientCanReachBenchmarkAndSignal() throws Exception {
		long productId = registerProduct.register(iphone17());

		mockMvc.perform(get("/api/v1/products/{id}/variants", productId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].variantId").isNumber())
				.andExpect(jsonPath("$[0].priceAxisValues.용량").exists());
	}

	@Test
	void unknownProductYieldsAnEmptyVariantList() throws Exception {
		mockMvc.perform(get("/api/v1/products/{id}/variants", 999_999L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	private RegisterProductCommand iphone17() {
		return new RegisterProductCommand(
				"아이폰 17",
				"phone",
				DemandAxisMode.GROUPED,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB", "512GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB")),
						new RegisterProductCommand.Variant("512GB", Map.of("용량", "512GB"))),
				List.of("아이폰17"));
	}
}
