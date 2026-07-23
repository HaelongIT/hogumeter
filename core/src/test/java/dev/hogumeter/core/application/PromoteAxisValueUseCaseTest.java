package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisEntity;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueEntity;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueRepository;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** USED-05 AC-17 ② — 메모 값을 축으로 승격(명시적 사용자 행위). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PromoteAxisValueUseCaseTest {

	@Autowired
	PromoteAxisValueUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	ListingRepository listings;
	@Autowired
	ComparisonAxisRepository axes;
	@Autowired
	ListingAxisValueRepository values;

	private Long listingId;
	private Long axisId;

	@BeforeEach
	void setUp() {
		Long productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		Long searchId = searches.save(new UsedSearchEntity(productId, "BUNJANG", List.of("아이폰17"),
				List.of(), null, 10)).getId();
		listingId = listings.save(new ListingEntity(searchId, "a1", "아이폰 17", 800_000L, Instant.now())).getId();
		axisId = axes.save(new ComparisonAxisEntity(productId, "배터리%")).getId();
	}

	@Test
	void promotesAValueToTheAxis() {
		useCase.promote(listingId, axisId, "92%");

		assertThat(values.findByListingIdAndAxisId(listingId, axisId)).get()
				.extracting(ListingAxisValueEntity::getValue).isEqualTo("92%");
	}

	@Test
	void rePromotingTheSameAxisOverwritesTheValue() {
		useCase.promote(listingId, axisId, "88%");
		useCase.promote(listingId, axisId, "92%"); // 재관찰 — 값 갱신

		List<ListingAxisValueEntity> all = values.findByListingId(listingId);
		assertThat(all).hasSize(1); // 유니크(listing_id, axis_id) — 새 행이 아니라 갱신
		assertThat(all.get(0).getValue()).isEqualTo("92%");
	}

	@Test
	void promotingToAnUnknownAxisThrows() {
		assertThatThrownBy(() -> useCase.promote(listingId, 999_999L, "x"))
				.isInstanceOf(ComparisonAxisNotFoundException.class);
	}

	@Test
	void promotingAnUnknownListingThrows() {
		assertThatThrownBy(() -> useCase.promote(999_999L, axisId, "x"))
				.isInstanceOf(ListingNotFoundException.class);
	}
}
