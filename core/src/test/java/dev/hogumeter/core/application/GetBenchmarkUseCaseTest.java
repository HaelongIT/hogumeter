package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.InvalidBenchmarkPeriodException;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.DealTags;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import jakarta.persistence.EntityManager;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 2 기준가 조회 — 저장된 deal_event → 도메인 매핑 → BenchmarkView(실 스키마 + Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetBenchmarkUseCaseTest {

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
	JdbcTemplate jdbc;
	@Autowired
	EntityManager em;

	private GetBenchmarkUseCase useCase;
	private long variantId;
	private int counter;

	@BeforeEach
	void setUp() {
		// 기본은 묶음(GROUPED) — 확정본의 기본값이고, 아래 대부분의 케이스는 수요축과 무관한 일반 산식이다.
		// 분리(SPLIT)는 축 값을 지정해야 조회가 되므로 그 케이스에서만 따로 만든다(Q-66 ①).
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();

		useCase = new GetBenchmarkUseCase(variants, dealEvents, mapper, vId -> 990_000L, params, demandScope,
				excludeKeywords, CLOCK);
	}

	private void insertCrossVerifiedDeal(long price, String dateIso) {
		insertCrossVerifiedDeal(variantId, price, dateIso, null);
	}

	private long insertCrossVerifiedDeal(long targetVariantId, long price, String dateIso, String demandAxisValue) {
		Instant when = Instant.parse(dateIso + "T00:00:00Z");
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + counter++,
				"https://ppomppu.test/" + counter, "제목", when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + counter++,
				"https://ruliweb.test/" + counter, "제목", when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(targetVariantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when,
				demandAxisValue));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
		return deal.getId();
	}

	/**
	 * 정상 딜을 넣은 뒤 {@code applied_conditions}에 배송비미상 표식을 <b>native SQL로</b> 심는다 —
	 * `DealEventEntity`의 그 컬럼 매핑은 읽기 전용(insertable=false)이라 `save()`로는 못 쓴다.
	 * 운영에선 `PreserveAppliedConditionsUseCase`가 같은 자리를 채운다(raw._derived → 컬럼).
	 */
	private void insertShippingUnknownDeal(long price, String dateIso) {
		long id = insertCrossVerifiedDeal(variantId, price, dateIso, null);
		jdbc.update("update deal_event set applied_conditions = array[?] where id = ?",
				DealTags.SHIPPING_UNKNOWN, id);
		// native UPDATE는 영속성 컨텍스트를 우회한다 — clear() 없이 findByVariantId를 부르면 캐시된
		// 엔티티(appliedConditions=null)가 나와 하한 딜이 제외되지 않는다(core-java.md). 운영에선 다른
		// 트랜잭션·틱이라 이 문제가 없다 — 이 clear는 한 tx로 압축한 테스트의 인공물이다.
		em.flush();
		em.clear();
	}

	@Test
	void computesSufficientBenchmarkFromPersistedDeals() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(view.benchmarkPrice()).isEqualTo(890_000L); // median
		assertThat(view.goodDealLine()).isEqualTo(850_000L); // P25(교차)
		assertThat(view.periodLowest().price()).isEqualTo(820_000L);
		assertThat(view.n()).isEqualTo(5);
		assertThat(view.m()).isEqualTo(5); // 전부 교차검증(2사이트 소스)
		assertThat(view.gap().vsBenchmark().won()).isEqualTo(100_000L); // 990k − 890k
	}

	/**
	 * Q-48 ①: <b>K는 사용자 손잡이다</b>(확정본 §217). 같은 표본(5건)이라도 사용자가 K를 10으로 올리면
	 * "아직 기준가를 말할 만큼은 아니다"가 되어 통계 대신 사례를 낸다. 이 배선이 없으면 전역 기본 5로만
	 * 판정해 손잡이가 저장만 되고 죽는다 — `alert_policy.k_display`는 V1부터 그렇게 죽어 있었다.
	 */
	@Test
	void userKDisplayMovesTheTier() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 10, List.of())); // 기본 5 → 10

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.tier()).as("n=5 < K=10이라 기준가를 말할 때가 아니다").isEqualTo(Tier.SPARSE);
		assertThat(view.benchmarkPrice()).isNull(); // 표본이 빈약하면 통계를 내지 않는다(절대 원칙 1)
		assertThat(view.cases()).hasSize(5); // 대신 사례를 그대로 — 판단은 사람이 한다
	}

	private void insertTitledDeal(long price, String dateIso, String title) {
		Instant when = Instant.parse(dateIso + "T00:00:00Z");
		RawDealPost r1 = rawPosts.save(new RawDealPost("ppomppu", "p" + counter++,
				"https://ppomppu.test/" + counter, title, when, "ACTIVE"));
		RawDealPost r2 = rawPosts.save(new RawDealPost("ruliweb", "r" + counter++,
				"https://ruliweb.test/" + counter, title, when, "ACTIVE"));
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				price, price, price, price, Origin.LIVE, true, OutlierFlag.NONE, false, DealStatus.VERIFIED, when, when,
				null));
		sources.save(new DealEventSourceEntity(deal.getId(), r1.getId(), "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), r2.getId(), "ruliweb"));
	}

	/**
	 * Q-28: 제외 키워드에 걸리는 딜은 <b>기준가 표본에서 빠진다</b> — 리퍼·벌크가 신품 기준가를 아래로 끌면
	 * "지금 사도 되나"가 거짓으로 초록이 된다. 판정은 조회 시점에 <b>지금</b>의 목록에 대고 하므로, 사용자가
	 * 목록을 고치면 이미 들어온 딜에도 소급 반영된다. {@code ExcludeKeywordPolicy}는 여기 오기 전 소비처 0이었다.
	 */
	@Test
	void excludeKeywordDropsMatchingDealFromSample() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");
		insertTitledDeal(780_000, "2026-06-20", "리퍼 아이폰 17 256GB"); // 신품보다 싼 함정
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of("리퍼")));

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.n()).as("리퍼 딜이 빠져 6이 아니라 5").isEqualTo(5);
		assertThat(view.periodLowest().price()).as("리퍼 780k가 최저가로 오르지 않는다").isEqualTo(820_000L);
		assertThat(view.benchmarkPrice()).isEqualTo(890_000L);
	}

	/**
	 * 거울상(무력화 방지): 같은 리퍼 딜이라도 제외 목록에 "리퍼"가 없으면 표본에 그대로 들어온다. 필터가
	 * <b>정책으로 구동</b>됨을 증명한다 — 이 테스트가 없으면 filter가 항상-제외/항상-통과여도 위 테스트만으론 안 잡힌다.
	 */
	@Test
	void keywordDealStaysWhenPolicyHasNoMatchingKeyword() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");
		insertTitledDeal(780_000, "2026-06-20", "리퍼 아이폰 17 256GB");
		policies.save(new AlertPolicyEntity(variantId, null, 6, null, null, 5, List.of("벌크"))); // 안 걸리는 키워드

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.n()).as("제외 목록에 안 걸리면 6건 그대로").isEqualTo(6);
		assertThat(view.periodLowest().price()).isEqualTo(780_000L);
	}

	/**
	 * Q-46 ②: `배송비미상` 딜은 저장가가 실결제가가 아니라 <b>하한</b>이라(배송비를 몰라 0을 더했다) 기준가를
	 * 아래로 끈다 → 값 통계(pricingSet)에서 뺀다. `DealSetsTest`가 순수 함수를 잠그지만, 그건 `DealEvent`를
	 * 손으로 만든다 — 여기선 <b>컬럼 → 매퍼 → 계산기</b> 다섯 구간이 실제로 흐르는지 종단으로 본다(그중 하나만
	 * 끊겨도 하한 딜이 조용히 기준가를 끌어내린다 — 실 폴링 전 필수, 절대 원칙 3 "놓침").
	 */
	@Test
	void shippingUnknownDealIsExcludedFromBenchmarkThroughTheColumn() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");
		insertShippingUnknownDeal(300_000, "2026-06-20"); // 배송비를 몰라 30만처럼 보이는 하한

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.n()).as("배송비미상 딜은 값 통계에서 빠져 6이 아니라 5").isEqualTo(5);
		assertThat(view.periodLowest().price()).as("하한 300k가 최저가로 오르지 않는다").isEqualTo(820_000L);
		assertThat(view.benchmarkPrice()).isEqualTo(890_000L);
	}

	/**
	 * 거울상(무력화 방지): 같은 300k 딜이라도 표식이 없으면 표본에 들어와 median·최저가를 끈다. 제외가
	 * <b>표식으로 구동</b>됨을 증명한다 — 이게 없으면 pricingSet 필터가 항상-제외/무력이어도 위 테스트만으론 안 잡힌다.
	 */
	@Test
	void sameDealWithoutShippingUnknownTagPullsTheBenchmarkDown() {
		insertCrossVerifiedDeal(820_000, "2026-06-10");
		insertCrossVerifiedDeal(850_000, "2026-06-12");
		insertCrossVerifiedDeal(890_000, "2026-06-14");
		insertCrossVerifiedDeal(920_000, "2026-06-16");
		insertCrossVerifiedDeal(950_000, "2026-06-18");
		insertCrossVerifiedDeal(300_000, "2026-06-20"); // 같은 값, 표식 없음

		BenchmarkView view = useCase.getBenchmark(variantId, 6, false);

		assertThat(view.n()).as("표식 없으면 6건 그대로").isEqualTo(6);
		assertThat(view.periodLowest().price()).as("하한 취급이 없으니 300k가 최저가로 온다").isEqualTo(300_000L);
	}

	@Test
	void throwsWhenVariantMissing() {
		assertThatThrownBy(() -> useCase.getBenchmark(9_999_999L, 6, false))
				.isInstanceOf(VariantNotFoundException.class);
	}

	@Test
	void throwsOnInvalidPeriod() {
		assertThatThrownBy(() -> useCase.getBenchmark(variantId, 0, false))
				.isInstanceOf(InvalidBenchmarkPeriodException.class);
	}

	/**
	 * Q-66 ①(확정본 §40·41): <b>분리(SPLIT)면 수요축 값별로 분포가 갈린다.</b> 지금까지 SPLIT은 저장만 되고
	 * 아무 동작도 바꾸지 못했다 — 모든 색이 한 분포에 섞여 median이 색과 무관한 값이 됐다.
	 */
	@Nested
	class SplitProduct {

		private long splitVariantId;

		@BeforeEach
		void setUpSplitProduct() {
			ProductEntity product = products.save(new ProductEntity("갤럭시 25", "스마트폰", DemandAxisMode.SPLIT));
			splitVariantId = variants.save(
					new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB"))).getId();
		}

		/** 기준가를 말하려면 K(기본 5)를 넘겨야 한다 — 색별로 5건씩 넣어야 분리가 의미를 갖는다. */
		private void insertBlackSample() {
			insertCrossVerifiedDeal(splitVariantId, 800_000, "2026-06-10", "블랙");
			insertCrossVerifiedDeal(splitVariantId, 810_000, "2026-06-11", "블랙");
			insertCrossVerifiedDeal(splitVariantId, 820_000, "2026-06-12", "블랙");
			insertCrossVerifiedDeal(splitVariantId, 830_000, "2026-06-13", "블랙");
			insertCrossVerifiedDeal(splitVariantId, 840_000, "2026-06-14", "블랙");
		}

		@Test
		@DisplayName("값별로 분포가 갈린다 — 블랙만 물으면 블랙 딜의 median이다")
		void splitsTheDistributionByDemandAxisValue() {
			insertBlackSample(); // median 820,000
			// 화이트는 훨씬 비싸다 — 섞였다면 n=7이고 median이 색과 무관한 값이 된다.
			insertCrossVerifiedDeal(splitVariantId, 980_000, "2026-06-16", "화이트");
			insertCrossVerifiedDeal(splitVariantId, 990_000, "2026-06-18", "화이트");

			BenchmarkView black = useCase.getBenchmark(splitVariantId, 6, false, "블랙");

			assertThat(black.n()).as("화이트가 섞이면 7이 된다").isEqualTo(5);
			assertThat(black.benchmarkPrice()).as("블랙 5건의 median").isEqualTo(820_000L);
		}

		/** 확정본 §41: <b>값 미상 딜은 기준가 계산에서 제외</b>된다 — 아무 분포에나 넣으면 그 분포가 오염된다. */
		@Test
		void unknownValueDealsAreExcludedFromSplitDistributions() {
			insertBlackSample();
			insertCrossVerifiedDeal(splitVariantId, 100_000, "2026-06-15", null); // 색 미상 — 아주 싸다

			BenchmarkView black = useCase.getBenchmark(splitVariantId, 6, false, "블랙");

			assertThat(black.n()).as("미상 딜은 어느 색 분포에도 못 들어간다").isEqualTo(5);
			assertThat(black.periodLowest().price()).as("미상의 10만원이 최저로 잡히면 안 된다").isEqualTo(800_000L);
		}

		/**
		 * <b>값을 안 주면 거절한다.</b> 전체 딜로 하나의 기준가를 내면 그게 곧 묶음이고, 사용자는 분리된
		 * 값을 보는 줄 안다 — 화면상 구분이 없어 그 거짓말은 조용하다(절대 원칙 1·6).
		 */
		@Test
		void refusesToAnswerWithoutADemandAxisValue() {
			insertCrossVerifiedDeal(splitVariantId, 800_000, "2026-06-10", "블랙");

			assertThatThrownBy(() -> useCase.getBenchmark(splitVariantId, 6, false, null))
					.isInstanceOf(DemandAxisValueRequiredException.class);
		}
	}
}
