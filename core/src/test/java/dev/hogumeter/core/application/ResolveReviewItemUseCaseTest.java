package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.DealEventEntity;
import dev.hogumeter.core.adapter.persistence.DealEventRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-15 승격·기각 배선. 읽기만 있고 쓰기가 없어 순수 도메인 {@code DealEvent.promoteFromOutlier()}·
 * {@code reject()}는 호출자 0이었고 {@code status}·{@code resolved_at}·{@code channel}은 죽은 컬럼이었다.
 * 여기서 그 배선의 <b>주입</b>을 시험한다 — 도메인 전이가 실제로 딜에 반영되고, 항목이 큐에서 내려간다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ResolveReviewItemUseCaseTest {

	private static final Instant T = Instant.parse("2026-07-01T00:00:00Z");

	@Autowired
	ResolveReviewItemUseCase resolve;
	@Autowired
	DealEventRepository dealEvents;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	JdbcTemplate jdbc;

	private long variantId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		VariantEntity variant = variants.save(new VariantEntity(product.getId(), "256GB", Map.of("용량", "256GB")));
		variantId = variant.getId();
	}

	private long outlierDeal() {
		DealEventEntity deal = dealEvents.save(new DealEventEntity(variantId, false, null,
				100_000L, 100_000L, 100_000L, 100_000L, Origin.LIVE, false,
				OutlierFlag.LOWER, false, DealStatus.ACTIVE, T, T));
		return deal.getId();
	}

	private long enqueue(String type, String payloadJson) {
		return jdbc.queryForObject("""
				insert into review_queue_item (type, payload, status)
				values (?, ?::jsonb, 'PENDING') returning id
				""", Long.class, type, payloadJson);
	}

	private String status(long itemId) {
		return jdbc.queryForObject("select status from review_queue_item where id = ?", String.class, itemId);
	}

	@Test
	void promoteClearsTheOutlierFlagAndTakesItOffTheQueue() {
		long dealId = outlierDeal();
		long itemId = enqueue("OUTLIER_LOWER", "{\"dealEventId\":" + dealId + ",\"priceFirst\":100000}");

		resolve.promote(itemId);

		assertThat(dealEvents.findById(dealId).orElseThrow().getOutlierFlag()).isEqualTo(OutlierFlag.NONE);
		assertThat(status(itemId)).isEqualTo("CONFIRMED");
		assertThat(jdbc.queryForObject("select channel from review_queue_item where id = ?", String.class, itemId))
				.isEqualTo("WEB");
	}

	@Test
	void rejectPermanentlyExcludesTheDealAndTakesItOffTheQueue() {
		long dealId = outlierDeal();
		long itemId = enqueue("OUTLIER_LOWER", "{\"dealEventId\":" + dealId + ",\"priceFirst\":100000}");

		resolve.reject(itemId);

		DealEventEntity deal = dealEvents.findById(dealId).orElseThrow();
		assertThat(deal.isPermanentlyExcluded()).isTrue();
		assertThat(deal.getOutlierFlag()).isEqualTo(OutlierFlag.LOWER); // 기각은 이상치 플래그를 지우지 않는다
		assertThat(status(itemId)).isEqualTo("REJECTED");
	}

	@Test
	void unclassifiedCanBeRejectedWithoutTouchingAnyDeal() {
		long itemId = enqueue("UNCLASSIFIED", "{\"title\":\"정체불명\",\"productCandidates\":[]}");

		resolve.reject(itemId);

		assertThat(status(itemId)).isEqualTo("REJECTED");
	}

	@Test
	void unclassifiedPromoteIsRefusedAndLeavesItPending() {
		long itemId = enqueue("UNCLASSIFIED", "{\"title\":\"정체불명\",\"productCandidates\":[]}");

		assertThatThrownBy(() -> resolve.promote(itemId))
				.isInstanceOf(UnclassifiedPromoteNotSupportedException.class);
		assertThat(status(itemId)).isEqualTo("PENDING"); // 막혔으면 큐에 그대로 남는다
	}

	@Test
	void resolvingMissingOrAlreadyResolvedItemThrows() {
		assertThatThrownBy(() -> resolve.reject(999_999L)).isInstanceOf(ReviewItemNotFoundException.class);

		long itemId = enqueue("OUTLIER_LOWER", "{\"dealEventId\":" + outlierDeal() + "}");
		resolve.reject(itemId);
		assertThatThrownBy(() -> resolve.reject(itemId))
				.as("이미 처리된 항목은 다시 처리할 수 없다(PENDING 행에만 건다)")
				.isInstanceOf(ReviewItemNotFoundException.class);
	}
}
