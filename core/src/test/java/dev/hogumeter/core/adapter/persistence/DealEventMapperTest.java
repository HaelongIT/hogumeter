package dev.hogumeter.core.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-16(코드리뷰 20260806) — {@code toDomain}의 "대표 원문" 선택이 정렬 없이 이뤄지면(파생 쿼리는
 * order by가 없으면 행 순서를 보장하지 않는다) 같은 딜을 두 번 조회했을 때 서로 다른 site/sourceUrl이
 * 나올 수 있다. {@code findByDealEventId}에 명시적 정렬을 걸어 재현 가능하게 만든다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DealEventMapperTest {

	private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

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

	private long variantId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
	}

	private long savePost(String site, String url) {
		return rawPosts.save(new RawDealPost(site, "post-" + site, url, "title", 890_000L, T, T, "ACTIVE")).getId();
	}

	@Test
	void representativeSiteAndUrlAreStableAcrossRepeatedReads() {
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				890_000L, 890_000L, 890_000L, 890_000L, Origin.LIVE, true, OutlierFlag.NONE, false,
				DealStatus.VERIFIED, T, T));
		long firstPostId = savePost("ppomppu", "https://ppomppu.test/1");
		long secondPostId = savePost("ruliweb", "https://ruliweb.test/1");
		sources.save(new DealEventSourceEntity(deal.getId(), firstPostId, "ppomppu"));
		sources.save(new DealEventSourceEntity(deal.getId(), secondPostId, "ruliweb"));

		DealEvent first = mapper.toDomain(deal);
		DealEvent second = mapper.toDomain(deal);

		// 같은 딜을 두 번 조회해도 대표 원문(site/sourceUrl)이 같아야 한다 — 정렬 없는 파생 쿼리는
		// 이 보장이 없다(PostgreSQL이 순서를 정하지 않는다).
		assertThat(first.site()).isEqualTo(second.site());
		assertThat(first.sourceUrl()).isEqualTo(second.sourceUrl());
		// 명시적으로 "먼저 저장된(=id가 작은) 소스"가 대표여야 한다(findByDealEventIdOrderByIdAsc).
		assertThat(first.site()).isEqualTo("ppomppu");
		assertThat(first.sourceUrl()).isEqualTo("https://ppomppu.test/1");
	}

	@Test
	void findByDealEventIdReturnsSourcesOrderedById() {
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				890_000L, 890_000L, 890_000L, 890_000L, Origin.LIVE, false, OutlierFlag.NONE, false,
				DealStatus.ACTIVE, T, T));
		long firstPostId = savePost("ppomppu", "https://ppomppu.test/1");
		long secondPostId = savePost("ruliweb", "https://ruliweb.test/1");
		DealEventSourceEntity firstSource = sources.save(new DealEventSourceEntity(deal.getId(), firstPostId, "ppomppu"));
		DealEventSourceEntity secondSource = sources.save(new DealEventSourceEntity(deal.getId(), secondPostId, "ruliweb"));

		List<DealEventSourceEntity> ordered = sources.findByDealEventId(deal.getId());

		assertThat(ordered).extracting(DealEventSourceEntity::getId)
				.containsExactly(firstSource.getId(), secondSource.getId());
	}
}
