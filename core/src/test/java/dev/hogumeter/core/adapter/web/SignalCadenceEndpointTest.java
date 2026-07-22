package dev.hogumeter.core.adapter.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import dev.hogumeter.core.application.GlobalExcludeKeywords;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** M5 배선 — SIG·CAD 조회 REST(저장된 deal_event로 compute-on-demand). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignalCadenceEndpointTest {

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
	GlobalExcludeKeywords globalKeywords;

	private long variantId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
		// 교차검증 5건 {820,850,890,920,950}k, 최근(신선) 분산 배치 → SUFFICIENT + 신선도 자격
		long[] prices = { 820_000, 850_000, 890_000, 920_000, 950_000 };
		for (int i = 0; i < prices.length; i++) {
			insertCrossVerifiedDeal(prices[i], Instant.now().minus(Duration.ofDays(6 - i)));
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

	@Test
	void signalEndpointReturnsGreenForFreshDealBelowP25() throws Exception {
		mockMvc.perform(get("/api/v1/variants/{id}/signal", variantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.color").value("GREEN")); // 최저 활성 priceLast 820k ≤ P25 850k
	}

	private void insertTitledDeal(long price, Instant when, String title) {
		RawDealPost raw = rawPosts.save(new RawDealPost("ppomppu", "t" + seq++, "https://p.test/t" + seq,
				title, when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), raw.getId(), "ppomppu"));
	}

	/**
	 * Q-28: <b>제외는 조용하다</b> — 걸러진 딜은 화면에서 흔적 없이 사라져 "원래 딜이 없었다"와 구별되지 않는다.
	 * 특히 <b>전역</b> 키워드는 모든 제품의 표본을 한꺼번에 갉아먹을 수 있어, 몇 건을 뺐는지 딱지로 보여야
	 * 사람이 "내 키워드가 과한가"를 알 수 있다.
	 */
	@Test
	void signalNotesHowManyDealsExcludeKeywordsRemoved() throws Exception {
		insertTitledDeal(780_000, Instant.now().minus(Duration.ofDays(1)), "리퍼 아이폰 17 256GB");
		globalKeywords.replace(List.of("리퍼"));

		mockMvc.perform(get("/api/v1/variants/{id}/signal", variantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notes", hasItem(containsString("1건 제외"))));
	}

	/** 거울상: 아무것도 안 걸리면 딱지를 달지 않는다 — 항상 켜진 딱지는 정보가 아니라 소음이다. */
	@Test
	void signalHasNoExclusionNoteWhenNothingWasExcluded() throws Exception {
		insertTitledDeal(780_000, Instant.now().minus(Duration.ofDays(1)), "리퍼 아이폰 17 256GB");
		globalKeywords.replace(List.of("벌크")); // 이 표본엔 안 걸리는 키워드

		mockMvc.perform(get("/api/v1/variants/{id}/signal", variantId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.notes", not(hasItem(containsString("제외")))));
	}

	@Test
	void cadenceEndpointReturnsEventCount() throws Exception {
		mockMvc.perform(get("/api/v1/variants/{id}/cadence", variantId).param("periodMonths", "6"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventCount").value(5))
				.andExpect(jsonPath("$.guardMet").value(true));
	}
}
