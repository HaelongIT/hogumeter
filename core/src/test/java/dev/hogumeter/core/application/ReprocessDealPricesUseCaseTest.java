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
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-27 ① 가격 변경 재처리 — 수집기가 업서트한 새 가격이 deal_event로 전파되는지(BM-01 AC-2).
 * 순수 산술은 {@code PriceRefreshTest}가 본다. 여기서 보는 것은 <b>이음새</b>다:
 * raw_deal_post → deal_event_source → deal_event.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReprocessDealPricesUseCaseTest {

	private static final Instant T1 = Instant.parse("2026-07-01T00:00:00Z");
	private static final Instant T2 = Instant.parse("2026-07-02T00:00:00Z"); // 재폴링

	@Autowired
	ReprocessDealPricesUseCase useCase;
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

	private long saveActiveDeal(long price, Instant lastSeen) {
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, false, OutlierFlag.NONE, false,
				DealStatus.ACTIVE, lastSeen, lastSeen));
		return deal.getId();
	}

	private void linkSource(long dealId, Long price, String status, Instant capturedAt) {
		RawDealPost post = rawPosts.save(new RawDealPost("ppomppu", "post" + seq++,
				"https://p.test/" + seq, "제목", price, null, capturedAt, status));
		sources.save(new DealEventSourceEntity(dealId, post.getId(), "ppomppu"));
	}

	private DealEventEntity reload(long dealId) {
		return dealEvents.findById(dealId).orElseThrow();
	}

	@Test
	@DisplayName("가격이 내리면 priceLast·priceMin이 따라간다. priceFirst는 그대로 (기준가 분포가 그 위에 선다)")
	void priceDropIsPropagated() {
		long dealId = saveActiveDeal(999_000, T1);
		linkSource(dealId, 900_000L, "ACTIVE", T2);

		useCase.reprocessPriceChanges();

		DealEventEntity deal = reload(dealId);
		assertThat(deal.getPriceLast()).isEqualTo(900_000);
		assertThat(deal.getPriceMin()).isEqualTo(900_000);
		assertThat(deal.getPriceFirst()).isEqualTo(999_000);
		assertThat(deal.getLastSeen()).isEqualTo(T2);
		assertThat(deal.getStatus()).as("종료 판정은 다른 유스케이스의 몫").isEqualTo(DealStatus.ACTIVE);
	}

	@Test
	@DisplayName("가격이 오르면 priceLast만 오르고 priceMin은 지나간 기회를 기억한다")
	void priceRiseKeepsTheOldOpportunity() {
		long dealId = saveActiveDeal(900_000, T1);
		linkSource(dealId, 1_100_000L, "ACTIVE", T2);

		useCase.reprocessPriceChanges();

		assertThat(reload(dealId).getPriceLast()).isEqualTo(1_100_000);
		assertThat(reload(dealId).getPriceMin()).isEqualTo(900_000);
	}

	@Test
	@DisplayName("품절된 원문의 가격은 \"지금\"이 아니다 — 그래도 지나간 기회에는 남는다")
	void soldOutSourceCannotSetTheCurrentPrice() {
		long dealId = saveActiveDeal(999_000, T1);
		linkSource(dealId, 950_000L, "ACTIVE", T1);
		linkSource(dealId, 800_000L, "SOLD_OUT", T2); // 더 최근이지만 살 수 없다

		useCase.reprocessPriceChanges();

		DealEventEntity deal = reload(dealId);
		assertThat(deal.getPriceLast()).isEqualTo(950_000);
		assertThat(deal.getPriceMin()).isEqualTo(800_000);
	}

	@Test
	@DisplayName("가격 없는 원문은 증거가 아니다 — 0원으로 읽으면 기준가가 무너진다(BM-02 AC-3)")
	void postsWithoutAPriceAreNotEvidence() {
		long dealId = saveActiveDeal(999_000, T1);
		linkSource(dealId, null, "ACTIVE", T2);

		useCase.reprocessPriceChanges();

		DealEventEntity deal = reload(dealId);
		assertThat(deal.getPriceLast()).isEqualTo(999_000);
		assertThat(deal.getPriceMin()).isEqualTo(999_000);
		assertThat(deal.getLastSeen()).as("증거가 없으니 lastSeen도 안 움직인다").isEqualTo(T1);
	}

	@Test
	@DisplayName("바뀐 게 없으면 lastSeen도 흔들지 않는다")
	void noChangeMeansNoWrite() {
		long dealId = saveActiveDeal(999_000, T2);
		linkSource(dealId, 999_000L, "ACTIVE", T1); // 과거 관측

		useCase.reprocessPriceChanges();

		assertThat(reload(dealId).getLastSeen()).isEqualTo(T2);
	}

	@Test
	@DisplayName("종료된 딜의 가격은 더 이상 움직이지 않는다")
	void endedDealsAreLeftAlone() {
		DealEventEntity ended = dealEvents.save(new DealEventEntity(variantId, false, null,
				999_000L, 999_000L, 999_000L, 999_000L, Origin.LIVE, false, OutlierFlag.NONE, false,
				DealStatus.ENDED, T1, T1));
		linkSource(ended.getId(), 500_000L, "ACTIVE", T2);

		useCase.reprocessPriceChanges();

		assertThat(reload(ended.getId()).getPriceLast()).isEqualTo(999_000);
	}
}
