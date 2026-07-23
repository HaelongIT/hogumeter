package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceEntity;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.used.EvaluationInput;
import dev.hogumeter.core.domain.used.EvaluationKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-04 평가기 배선(Testcontainers). {@code ListingExtractor}·{@code UsedRiskSignals}·
 * {@code PriceContextCalculator}를 조립해 처음으로 REST 소비자를 만든다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EvaluateListingUseCaseTest {

	@Autowired
	EvaluateListingUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	ListingRepository listings;
	@Autowired
	VariantRepository variants;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	RawDealPostRepository rawPosts;

	private Long searchId;
	private Long productId;
	private int seq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		productId = product.getId();
		searchId = searches.save(new UsedSearchEntity(productId, "BUNJANG", List.of("아이폰17"),
				List.of("이민 급처"), 900_000L, 10)).getId();
	}

	@Test
	@DisplayName("AC-12 MANUAL: 수동 입력은 바로 구조화된다")
	void manualInputResolvesDirectly() {
		EvaluationOutcome outcome = useCase.evaluate(searchId,
				EvaluationInput.manual("아이폰 17 256 S급", 850_000L, "https://m.example/1"), null);

		assertThat(outcome.needsInput()).isNull();
		assertThat(outcome.listing().title()).isEqualTo("아이폰 17 256 S급");
		assertThat(outcome.listing().price()).isEqualTo(850_000L);
	}

	@Test
	@DisplayName("AC-12 TEXT 실패 → MANUAL을 요청한다 — 지어내지 않고 폴백을 가리킨다")
	void textFailureAsksForManual() {
		EvaluationOutcome outcome = useCase.evaluate(searchId, EvaluationInput.text("가격은 쪽지로 문의"), null);

		assertThat(outcome.needsInput()).isEqualTo(EvaluationKind.MANUAL);
		assertThat(outcome.listing()).isNull();
	}

	@Test
	@DisplayName("AC-12 URL: 이미 폴링해 아는 매물이면 실 fetch 없이 스냅샷으로 해결한다")
	void urlResolvesFromAlreadyPolledSnapshotWithoutFetching() {
		listings.save(new ListingEntity(searchId, "a1", "아이폰 17 256 미개봉", 880_000L, Instant.now(),
				"https://m.bunjang.co.kr/products/a1"));

		EvaluationOutcome outcome = useCase.evaluate(searchId,
				EvaluationInput.url("https://m.bunjang.co.kr/products/a1"), null);

		assertThat(outcome.needsInput()).isNull();
		assertThat(outcome.listing().price()).isEqualTo(880_000L);
	}

	@Test
	@DisplayName("AC-12 URL: 모르는 매물이면(폴링 안 됨) 실 fetch 대신 TEXT를 요청한다")
	void unknownUrlAsksForText() {
		EvaluationOutcome outcome = useCase.evaluate(searchId,
				EvaluationInput.url("https://m.bunjang.co.kr/products/unknown"), null);

		assertThat(outcome.needsInput()).isEqualTo(EvaluationKind.TEXT);
	}

	@Test
	@DisplayName("AC-13 위험 신호: exclude 키워드 히트가 나열된다 — 판정 문구 없이")
	void riskSignalsListExcludeKeywordHits() {
		EvaluationOutcome outcome = useCase.evaluate(searchId,
				EvaluationInput.manual("아이폰 17 이민 급처 팝니다", 700_000L, null), null);

		assertThat(outcome.riskSignals()).extracting("detail").contains("이민 급처");
		assertThat(outcome.riskSignals().stream().map(Object::toString))
				.noneMatch(s -> s.contains("사기") || s.contains("위험"));
	}

	@Test
	@DisplayName("AC-13 가격 맥락: 활성 매물 스냅샷이 가공 없이 실린다")
	void priceContextListsActiveSnapshot() {
		listings.save(new ListingEntity(searchId, "a1", "아이폰 17", 800_000L, Instant.now()));
		listings.save(new ListingEntity(searchId, "a2", "아이폰 17", 950_000L, Instant.now()));

		EvaluationOutcome outcome = useCase.evaluate(searchId,
				EvaluationInput.manual("아이폰 17 256", 850_000L, null), null);

		assertThat(outcome.priceContext().activeSnapshotPrices()).containsExactlyInAnyOrder(800_000L, 950_000L);
		assertThat(outcome.priceContext().source()).contains("번개");
	}

	@Test
	@DisplayName("variantId를 주면 신품 기준가 대비 %를 낸다 — 안 주면 null(과대약속 금지)")
	void benchmarkPercentOnlyWhenVariantIdGiven() {
		VariantEntity variant = variants.save(new VariantEntity(productId, "256GB", java.util.Map.of()));
		for (int i = 0; i < 5; i++) {
			long price = 1_000_000L + i * 10_000L;
			Instant when = Instant.now().minus(Duration.ofDays(i + 1));
			RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + seq++, "https://p.test/" + seq,
					"제목", when, "ACTIVE"));
			RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + seq++, "https://r.test/" + seq,
					"제목", when, "ACTIVE"));
			DealEventEntity deal = dealEvents.save(new DealEventEntity(variant.getId(), false, null,
					price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED,
					when, when));
			sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
			sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
		}

		EvaluationOutcome withVariant = useCase.evaluate(searchId,
				EvaluationInput.manual("아이폰 17 256", 800_000L, null), variant.getId());
		EvaluationOutcome withoutVariant = useCase.evaluate(searchId,
				EvaluationInput.manual("아이폰 17 256", 800_000L, null), null);

		assertThat(withVariant.priceContext().benchmarkComparisonPercent()).isNotNull();
		assertThat(withoutVariant.priceContext().benchmarkComparisonPercent()).isNull();
	}

	@Test
	@DisplayName("없는 검색은 404 계열 예외")
	void unknownSearchThrows() {
		assertThatThrownBy(() -> useCase.evaluate(999_999L, EvaluationInput.manual("x", 1L, null), null))
				.isInstanceOf(UsedSearchNotFoundException.class);
	}
}
