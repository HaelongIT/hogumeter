package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.adapter.persistence.WatchItemRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.watch.PinState;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** WATCH(docs/17) HTTP 경로 — 핀 생성·조회·결말이 실제로 저장까지 닿는지. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WatchControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	WatchItemRepository watchItems;

	private long deal() {
		long productId = products.save(new ProductEntity("WATCH 컨트롤러 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		return dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000, 900_000,
				Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();
	}

	@Test
	void postCreatesAPinAndGetListsIt() throws Exception {
		long dealId = deal();

		String pinId = mockMvc.perform(post("/api/v1/watch-items").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dealEventId\": " + dealId + ", \"note\": \"메모\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.watchItemId").isNumber())
				.andReturn().getResponse().getContentAsString();

		assertThat(pinId).contains("watchItemId");
		mockMvc.perform(get("/api/v1/watch-items"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.dealEventId == " + dealId + ")].note").value("메모"));
	}

	@Test
	void postOnAnEndedDealReturns400WithDomainCode() throws Exception {
		long productId = products.save(new ProductEntity("종료 딜 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		long dealId = dealEvents.save(new DealEventEntity(variantId, false, null, 900_000, 900_000, 900_000,
				900_000, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ENDED,
				Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"))).getId();

		mockMvc.perform(post("/api/v1/watch-items").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dealEventId\": " + dealId + ", \"note\": null}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("WATCH_DEAL_NOT_PINNABLE"));
	}

	@Test
	void boughtResolvesThePinAndRemovesItFromTheActiveList() throws Exception {
		long dealId = deal();
		String body = mockMvc.perform(post("/api/v1/watch-items").contentType(MediaType.APPLICATION_JSON)
				.content("{\"dealEventId\": " + dealId + ", \"note\": null}"))
				.andReturn().getResponse().getContentAsString();
		long watchItemId = Long.parseLong(body.replaceAll("\\D+", ""));

		mockMvc.perform(post("/api/v1/watch-items/" + watchItemId + "/bought"))
				.andExpect(status().isNoContent());

		assertThat(watchItems.findById(watchItemId).orElseThrow().getState()).isEqualTo(PinState.BOUGHT);
		mockMvc.perform(get("/api/v1/watch-items"))
				.andExpect(jsonPath("$[?(@.watchItemId == " + watchItemId + ")]").isEmpty());
	}
}
