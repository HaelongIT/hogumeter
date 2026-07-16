package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 2 기준가 조회 HTTP — GET 200 + BenchmarkView JSON, 에러코드 매핑(실 clock, 최근 상대 날짜). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BenchmarkControllerTest {

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

	private long variantId;
	private int counter;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
	}

	private void insertCrossVerifiedDeal(long price, int daysAgo) {
		Instant when = Instant.now().minus(Duration.ofDays(daysAgo));
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + counter++,
				"https://ppomppu.test/" + counter, "제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + counter++,
				"https://ruliweb.test/" + counter, "제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
	}

	@Test
	void getReturnsBenchmarkView() throws Exception {
		insertCrossVerifiedDeal(820_000, 30);
		insertCrossVerifiedDeal(850_000, 25);
		insertCrossVerifiedDeal(890_000, 20);
		insertCrossVerifiedDeal(920_000, 15);
		insertCrossVerifiedDeal(950_000, 10);

		mockMvc.perform(get("/api/v1/variants/{id}/benchmark", variantId).param("periodMonths", "6"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tier").value("SUFFICIENT"))
				.andExpect(jsonPath("$.benchmarkPrice").value(890_000))
				.andExpect(jsonPath("$.n").value(5))
				.andExpect(jsonPath("$.m").value(5));
	}

	@Test
	void invalidPeriodReturns400WithCode() throws Exception {
		mockMvc.perform(get("/api/v1/variants/{id}/benchmark", variantId).param("periodMonths", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BM_INVALID_PERIOD"));
	}

	@Test
	void missingVariantReturns404WithCode() throws Exception {
		mockMvc.perform(get("/api/v1/variants/{id}/benchmark", 9_999_999L).param("periodMonths", "6"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("BM_VARIANT_NOT_FOUND"));
	}
}
