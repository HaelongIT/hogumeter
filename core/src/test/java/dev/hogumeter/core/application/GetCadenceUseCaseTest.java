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
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.cadence.CadenceView;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * CAD 딜 주기 조회 — 제외 키워드·수요축 스코프가 기준가·신호등과 같은 표본을 봐야 한다(docs/91 Q-29 인접 발견).
 * {@code GetSignalUseCase}·{@code GetBenchmarkUseCase}는 배선돼 있었으나 {@code GetCadenceUseCase}는
 * 저장된 딜 전부를 그대로 넘기고 있었다 — 리퍼 딜이 섞이거나 SPLIT 제품에서 색이 섞인 주기가 나올 수 있었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetCadenceUseCaseTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

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
	DealEventMapper mapper;
	@Autowired
	VariantBenchmarkParams params;
	@Autowired
	AlertPolicyRepository policies;
	@Autowired
	VariantDemandScope demandScope;
	@Autowired
	VariantExcludeKeywords excludeKeywords;
	@Autowired
	GlobalExcludeKeywords globalKeywords;

	private GetCadenceUseCase useCase;
	private long variantId;
	private int counter;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();

		useCase = new GetCadenceUseCase(variants, dealEvents, mapper, demandScope, excludeKeywords, CLOCK);
	}

	private void insertDeal(long targetVariantId, long price, String dateIso, String title, String demandAxisValue) {
		Instant when = Instant.parse(dateIso + "T00:00:00Z");
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + counter++,
				"https://ppomppu.test/" + counter, title, when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(targetVariantId, false, null,
				price, price, price, price, Origin.LIVE, false, OutlierFlag.NONE, false, DealStatus.ACTIVE, when, when,
				demandAxisValue));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
	}

	private void insertFiveDeals() {
		insertDeal(variantId, 820_000, "2026-06-10", "제목", null);
		insertDeal(variantId, 850_000, "2026-06-11", "제목", null);
		insertDeal(variantId, 890_000, "2026-06-12", "제목", null);
		insertDeal(variantId, 920_000, "2026-06-13", "제목", null);
		insertDeal(variantId, 950_000, "2026-06-14", "제목", null);
	}

	/**
	 * Q-28과 같은 표본이어야 한다 — 리퍼 딜이 주기(발생 빈도)에 섞이면 "이 제품은 자주 뜬다"는 판단이 거짓이 된다.
	 */
	@Test
	void excludesKeywordMatchedDealsFromEventCount() {
		insertFiveDeals();
		insertDeal(variantId, 700_000, "2026-06-20", "리퍼 아이폰 17 256GB", null);
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of("리퍼"), List.of()));

		CadenceView view = useCase.getCadence(variantId, 6);

		assertThat(view.eventCount()).as("리퍼 딜이 빠져 6이 아니라 5").isEqualTo(5);
	}

	/** 거울상: 안 걸리는 키워드면 그대로 6건. */
	@Test
	void keepsDealWhenPolicyHasNoMatchingKeyword() {
		insertFiveDeals();
		insertDeal(variantId, 700_000, "2026-06-20", "리퍼 아이폰 17 256GB", null);
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of("벌크"), List.of()));

		CadenceView view = useCase.getCadence(variantId, 6);

		assertThat(view.eventCount()).isEqualTo(6);
	}

	@Nested
	class SplitProduct {

		private long splitVariantId;

		@BeforeEach
		void setUpSplitProduct() {
			ProductEntity product = products.save(new ProductEntity("갤럭시 25", "스마트폰", DemandAxisMode.SPLIT));
			splitVariantId = variants.save(
					new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB"))).getId();
		}

		/**
		 * Q-66과 같은 표본이어야 한다 — 판단 화면은 이미 색별로 기준가·신호등을 가르는데, 바로 아래 딜 주기 줄만
		 * 색을 안 가르면 같은 화면이 서로 다른 사실을 말한다.
		 */
		@Test
		@DisplayName("SPLIT 제품은 값을 지정해야 하고, 그 값의 딜만 센다")
		void splitsEventCountByDemandAxisValue() {
			insertDeal(splitVariantId, 800_000, "2026-06-10", "제목", "블랙");
			insertDeal(splitVariantId, 810_000, "2026-06-11", "제목", "블랙");
			insertDeal(splitVariantId, 820_000, "2026-06-12", "제목", "블랙");
			insertDeal(splitVariantId, 980_000, "2026-06-16", "제목", "화이트");
			insertDeal(splitVariantId, 990_000, "2026-06-18", "제목", "화이트");

			CadenceView black = useCase.getCadence(splitVariantId, 6, "블랙");

			assertThat(black.eventCount()).as("화이트가 섞이면 5가 된다").isEqualTo(3);
		}

		@Test
		@DisplayName("값 없이 물으면 거절한다 — 전체로 답하면 묶음의 거짓말")
		void rejectsWhenValueMissing() {
			insertDeal(splitVariantId, 800_000, "2026-06-10", "제목", "블랙");

			org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.getCadence(splitVariantId, 6, null))
					.isInstanceOf(DemandAxisValueRequiredException.class);
		}
	}
}
