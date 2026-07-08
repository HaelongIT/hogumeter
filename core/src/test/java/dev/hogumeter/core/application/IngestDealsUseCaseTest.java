package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.DealEventSourceRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.RawDealPost;
import dev.hogumeter.core.adapter.persistence.RawDealPostRepository;
import dev.hogumeter.core.adapter.persistence.ReviewQueueItemRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 슬라이스 3 수집 파이프라인 — 매칭→병합→deal_event 저장, 애매→리뷰큐(Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IngestDealsUseCaseTest {

	private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	IngestDealsUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	AliasRepository aliases;
	@Autowired
	RawDealPostRepository rawPosts;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	DealEventSourceRepository sources;
	@Autowired
	ReviewQueueItemRepository reviewQueue;

	private long variantId;
	private int postSeq;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.SPLIT));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		aliases.save(new AliasEntity(product.getId(), "아이폰17"));
		variantId = variant.getId();
	}

	private void savePost(String site, String title, Long price, Instant when) {
		rawPosts.save(new RawDealPost(site, "post" + postSeq++, "https://" + site + ".test/" + postSeq,
				title, price, when, when, "ACTIVE"));
	}

	@Test
	void confirmedMatchCreatesDealEvent() {
		savePost("ppomppu", "아이폰 17 256기가 자급제 89만", 890_000L, T);

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(1);
		assertThat(deals.get(0).getPriceFirst()).isEqualTo(890_000L);
		assertThat(sources.findByDealEventId(deals.get(0).getId())).hasSize(1);
	}

	@Test
	void secondSiteMergesIntoVerifiedDeal() {
		savePost("ppomppu", "아이폰 17 256기가 89만", 890_000L, T);
		savePost("ruliweb", "아이폰 17 256기가 특가", 895_000L, T.plus(Duration.ofHours(6)));

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(1); // 병합
		assertThat(deals.get(0).getStatus()).isEqualTo(DealStatus.VERIFIED);
		assertThat(sources.findByDealEventId(deals.get(0).getId())).hasSize(2); // 2사이트 소스
	}

	@Test
	void noPricePostIsSkipped() {
		savePost("ppomppu", "아이폰 17 256기가 팝니다 문의", null, T);

		useCase.ingestPending();

		assertThat(dealEvents.findByVariantId(variantId)).isEmpty();
	}

	@Test
	void ambiguousMatchEnqueuesReviewItem() {
		savePost("ppomppu", "애플 아이폰 신형 256기가", 800_000L, T); // "17" 없음 → CANDIDATE

		useCase.ingestPending();

		assertThat(dealEvents.findByVariantId(variantId)).isEmpty();
		assertThat(reviewQueue.findByType(ReviewQueueType.UNCLASSIFIED)).hasSize(1);
	}

	@Test
	void lowerOutlierIsFlaggedAndQueued() {
		// 병합 안 되는 5건(30k 간격 > 허용폭) + 1건 대박(이상치)
		for (long price : new long[] { 800_000, 830_000, 860_000, 890_000, 920_000 }) {
			savePost("ppomppu", "아이폰 17 256기가 특가", price, T);
		}
		savePost("ppomppu", "아이폰 17 256기가 특가", 100_000L, T); // 분포 대비 하향 이상치

		useCase.ingestPending();

		List<DealEventEntity> deals = dealEvents.findByVariantId(variantId);
		assertThat(deals).hasSize(6); // 전부 별개(간격이 허용폭 초과)
		DealEventEntity low = deals.stream().filter(d -> d.getPriceFirst() == 100_000L).findFirst().orElseThrow();
		assertThat(low.getOutlierFlag()).isEqualTo(OutlierFlag.LOWER);
		assertThat(reviewQueue.findByType(ReviewQueueType.OUTLIER_LOWER)).hasSize(1);
	}
}
