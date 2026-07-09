package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-27 상태변화 재처리 — 수집기가 업서트한 원문 상태(SOLD_OUT/DELETED)가 deal_event.ENDED로 전파되는지.
 * 이음새 테스트(raw_deal_post 상태 → deal_event): 부품별 GREEN이 못 잡던 갭(docs/99).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReprocessDealStatusUseCaseTest {

	private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
	private static final Instant T2 = Instant.parse("2026-07-02T00:00:00Z"); // T1 이후 재폴링

	@Autowired
	ReprocessDealStatusUseCase useCase;
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
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.SPLIT));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
	}

	private long saveActiveDeal(Instant lastSeen) {
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				890_000L, 890_000L, 890_000L, 890_000L, Origin.LIVE, false, OutlierFlag.NONE, false,
				DealStatus.ACTIVE, lastSeen, lastSeen));
		return deal.getId();
	}

	/** 원문 저장 + deal에 링크. status/captured_at은 수집기 업서트 결과를 흉내. */
	private void linkSource(long dealId, String status, Instant capturedAt) {
		RawDealPost post = rawPosts.save(new RawDealPost("ppomppu", "post" + seq++,
				"https://p.test/" + seq, "제목", capturedAt, status));
		sources.save(new DealEventSourceEntity(dealId, post.getId(), "ppomppu"));
	}

	private DealEventEntity reload(long dealId) {
		return dealEvents.findById(dealId).orElseThrow();
	}

	@Test
	void singleSoldOutSourceEndsTheDeal() {
		long dealId = saveActiveDeal(T1);
		linkSource(dealId, "SOLD_OUT", T2);

		useCase.reprocessEndedDeals();

		DealEventEntity ended = reload(dealId);
		assertThat(ended.getStatus()).isEqualTo(DealStatus.ENDED);
		assertThat(ended.getLastSeen()).isEqualTo(T2); // 종료 근거 시각으로 갱신
	}

	@Test
	void deletedSourceAlsoEndsTheDeal() {
		long dealId = saveActiveDeal(T1);
		linkSource(dealId, "DELETED", T2);

		useCase.reprocessEndedDeals();

		assertThat(reload(dealId).getStatus()).isEqualTo(DealStatus.ENDED);
	}

	@Test
	void oneStillActiveSourceKeepsTheDealActive() {
		long dealId = saveActiveDeal(T1);
		linkSource(dealId, "SOLD_OUT", T2);
		linkSource(dealId, "ACTIVE", T2); // 다른 사이트에서 여전히 구매 가능

		useCase.reprocessEndedDeals();

		DealEventEntity still = reload(dealId);
		assertThat(still.getStatus()).isEqualTo(DealStatus.ACTIVE);
		assertThat(still.getLastSeen()).isEqualTo(T1); // 무변
	}

	@Test
	void allActiveSourcesLeaveDealUnchanged() {
		long dealId = saveActiveDeal(T1);
		linkSource(dealId, "ACTIVE", T2);

		useCase.reprocessEndedDeals();

		assertThat(reload(dealId).getStatus()).isEqualTo(DealStatus.ACTIVE);
	}
}
