package dev.hogumeter.core.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import java.time.Instant;
import java.util.List;
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

/** PUR-01/02 배선 — 구매 기록 + 구매 시점 as-of 스냅샷 동결(Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseEndpointTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	PurchaseRepository purchases;

	private long variantId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.SPLIT));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
		// 교차검증 5건 {820,850,890,920,950}k @ 2026-01-01..05 → SUFFICIENT, 기준가 890k
		long[] prices = { 820_000, 850_000, 890_000, 920_000, 950_000 };
		for (int i = 0; i < prices.length; i++) {
			insertCrossVerifiedDeal(prices[i], Instant.parse("2026-01-0" + (i + 1) + "T00:00:00Z"));
		}
	}

	private void insertCrossVerifiedDeal(long price, Instant when) {
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + seq++, "https://p.test/" + seq,
				"제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + seq++, "https://r.test/" + seq,
				"제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
	}

	private String body(long paidPrice, String purchasedAt) {
		return """
				{"variantId":%d,"demandAxisValue":"256GB","paidPrice":%d,"purchasedAt":"%s"}"""
				.formatted(variantId, paidPrice, purchasedAt);
	}

	@Test
	void recordsPurchaseWithFrozenAsOfSnapshot() throws Exception {
		mockMvc.perform(post("/api/v1/purchases").contentType(MediaType.APPLICATION_JSON)
						.content(body(940_000L, "2026-02-01T00:00:00Z")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.purchaseId").isNumber());

		List<PurchaseEntity> saved = purchases.findByVariantId(variantId);
		assertThat(saved).hasSize(1);
		PurchaseEntity p = saved.get(0);
		assertThat(p.getState()).isEqualTo(PurchaseState.OBSERVING);
		assertThat(p.getSnapBenchmarkPrice()).isEqualTo(890_000L); // as-of 기준가
		assertThat(p.getSnapPaidGap()).isEqualTo(50_000L); // 940k − 890k = 호구 방향 +50k
		assertThat(p.isSnapUnobserved()).isFalse();
	}

	@Test
	void purchaseBeforeObservationStartIsUnobserved() throws Exception {
		// 최초 딜(2026-01-01)보다 이전 구매
		mockMvc.perform(post("/api/v1/purchases").contentType(MediaType.APPLICATION_JSON)
						.content(body(940_000L, "2025-12-01T00:00:00Z")))
				.andExpect(status().isCreated());

		PurchaseEntity p = purchases.findByVariantId(variantId).get(0);
		assertThat(p.isSnapUnobserved()).isTrue();
		assertThat(p.getSnapBenchmarkPrice()).isNull(); // 통계 없음(정직성)
		assertThat(p.getSnapPaidGap()).isNull();
	}
}
