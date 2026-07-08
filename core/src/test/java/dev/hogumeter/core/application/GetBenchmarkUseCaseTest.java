package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
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
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

	private GetBenchmarkUseCase useCase;
	private long variantId;
	private int counter;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.SPLIT));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();

		useCase = new GetBenchmarkUseCase(variants, dealEvents, mapper, vId -> 990_000L, CLOCK);
	}

	private void insertCrossVerifiedDeal(long price, String dateIso) {
		Instant when = Instant.parse(dateIso + "T00:00:00Z");
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
}
