package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AlertPolicyEntity;
import dev.hogumeter.core.adapter.persistence.AlertPolicyRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventMapper;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReportCardRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.ObservationContext;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-05 관찰 문맥 조회 — 제외 키워드·수요축 스코프가 기준가·신호등과 같은 표본을 봐야 한다(docs/91 Q-29 인접 발견).
 * {@code GetPurchaseObservationsUseCase}는 variant의 딜을 필터 없이 그대로 넘기고 있었다 — 리퍼 딜이 "활성
 * 최저가"로 잡히거나, SPLIT 제품에서 다른 색 딜이 "상회분"·"더 싼 기회"에 섞일 수 있었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetPurchaseObservationsUseCaseTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	PurchaseRepository purchases;
	@Autowired
	ReportCardRepository reportCards;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventMapper mapper;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	AlertPolicyRepository policies;
	@Autowired
	VariantDemandScope demandScope;
	@Autowired
	VariantExcludeKeywords excludeKeywords;

	private GetPurchaseObservationsUseCase useCase;
	private long variantId;
	private int counter;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();

		useCase = new GetPurchaseObservationsUseCase(variants, purchases, reportCards, dealEvents, mapper,
				demandScope, excludeKeywords, CLOCK);
	}

	private void insertActiveDeal(long targetVariantId, long priceLast, String title, String demandAxisValue) {
		Instant when = Instant.parse("2026-07-10T00:00:00Z");
		RawDealPost raw = rawPosts.save(new RawDealPost("ppomppu", "p" + counter++,
				"https://p.test/" + counter, title, when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(targetVariantId, false, null,
				priceLast, priceLast, priceLast, priceLast, Origin.LIVE, true, OutlierFlag.NONE, false,
				DealStatus.ACTIVE, when, when, demandAxisValue));
		sources.save(new DealEventSourceEntity(deal.getId(), raw.getId(), "ppomppu"));
	}

	/**
	 * Q-28과 같은 표본이어야 한다 — 리퍼 딜이 "활성 최저가"로 잡히면 "상회분"이 거짓으로 커진다(또는 작아진다).
	 */
	@Test
	void excludesKeywordMatchedDealFromActiveLowest() {
		insertActiveDeal(variantId, 700_000, "리퍼 아이폰 17 256GB", null); // 신품보다 훨씬 싼 함정
		insertActiveDeal(variantId, 850_000, "제목", null);
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of("리퍼"), List.of()));
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, null, 950_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		List<PurchaseObservation> observations = useCase.forVariant(variantId);

		assertThat(observations).hasSize(1);
		ObservationContext context = observations.get(0).context();
		assertThat(context.mode()).isEqualTo(ObservationContext.Mode.ACTIVE_DEAL);
		assertThat(context.activeLowestPriceLast()).as("리퍼 700k가 활성 최저가로 잡히면 안 된다").isEqualTo(850_000L);
	}

	@Test
	void splitProductScopesActiveLowestToThePurchasesOwnDemandAxisValue() {
		ProductEntity splitProduct = products.save(new ProductEntity("갤럭시 25", "스마트폰", DemandAxisMode.SPLIT));
		long splitVariantId = variants.save(
				new VariantEntity(splitProduct.getId(), "256GB", Map.of("용량", "256GB"))).getId();
		insertActiveDeal(splitVariantId, 800_000, "제목", "블랙");
		insertActiveDeal(splitVariantId, 700_000, "제목", "화이트"); // 다른 색 — 블랙 구매엔 안 보여야 함
		purchases.save(new PurchaseEntity(
				Purchase.observing(splitVariantId, "블랙", 950_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		List<PurchaseObservation> observations = useCase.forVariant(splitVariantId);

		assertThat(observations).hasSize(1);
		ObservationContext context = observations.get(0).context();
		assertThat(context.activeLowestPriceLast()).as("화이트 700k가 섞이면 안 된다").isEqualTo(800_000L);
	}
}
