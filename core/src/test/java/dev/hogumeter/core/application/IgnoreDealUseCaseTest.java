package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.DealIgnoreRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-22 사후학습: [무시]가 딜을 노이즈로 기록하고(멱등), 같은 variant 무시 제목들에서 빈출 토큰이 나오면
 * KEYWORD_SUGGEST 큐를 만든다. 자동 반영은 없다 — 제안만(판단은 사람).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IgnoreDealUseCaseTest {

	@Autowired
	IgnoreDealUseCase useCase;
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
	DealIgnoreRepository ignores;
	@Autowired
	ReviewQueueItemRepository reviewQueue;

	private long variantId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		variantId = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB"))).getId();
	}

	private long insertDeal(String title) {
		Instant when = Instant.parse("2026-07-20T00:00:00Z");
		int n = seq++;
		RawDealPost r = rawPosts.save(new RawDealPost("ppomppu", "p" + n, "https://ppomppu.test/" + n, title, when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null, 500_000, 500_000, 500_000,
				500_000, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when));
		sources.save(new DealEventSourceEntity(deal.getId(), r.getId(), "ppomppu"));
		return deal.getId();
	}

	@Test
	void ignoreRecordsNoiseIdempotently() {
		long dealId = insertDeal("리퍼 상품A");

		useCase.ignore(dealId);
		useCase.ignore(dealId); // 같은 알림 두 번 눌러도

		assertThat(ignores.findByVariantId(variantId)).hasSize(1);
	}

	@Test
	void frequentTokenAcrossIgnoresBecomesKeywordSuggestion() {
		useCase.ignore(insertDeal("리퍼 상품A")); // "리퍼" 1회 — 아직 제안 없음
		assertThat(pendingKeywordSuggestions()).isZero();

		useCase.ignore(insertDeal("리퍼 상품B")); // "리퍼" 2회 → 후보

		assertThat(pendingKeywordSuggestions()).as("빈출 토큰이 KEYWORD_SUGGEST로 제안된다").isEqualTo(1);
	}

	private long pendingKeywordSuggestions() {
		return reviewQueue.findAll().stream()
				.filter(i -> i.getType() == ReviewQueueType.KEYWORD_SUGGEST)
				.count();
	}
}
