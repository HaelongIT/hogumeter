package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisEntity;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueEntity;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueRepository;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteRepository;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.application.GetComparisonUseCase.ComparisonRow;
import dev.hogumeter.core.application.GetComparisonUseCase.ComparisonView;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-05 AC-18 — 병렬 비교표 데이터(축 정렬 + 빈칸은 체크리스트 + 매물별 메모 전문).
 * 통계 가공은 없다 — 매물 나열 + 축값 매트릭스 + 메모 전문이 전부다(가격 맥락은 USED-04의 몫).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetComparisonUseCaseTest {

	@Autowired
	GetComparisonUseCase useCase;
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
	@Autowired
	ListingNoteRepository notes;

	private Long productId;
	private Long searchId;

	@BeforeEach
	void setUp() {
		productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
		searchId = searches.save(new UsedSearchEntity(productId, "BUNJANG", List.of("아이폰17"),
				List.of(), null, 10)).getId();
	}

	@Test
	void listsAxesAndListingsWithValuesAndNotes() {
		Long axis1 = axes.save(new ComparisonAxisEntity(productId, "배터리%")).getId();
		Long axis2 = axes.save(new ComparisonAxisEntity(productId, "구성")).getId();
		Long listingId = listings.save(new ListingEntity(searchId, "a1", "아이폰 17 256", 800_000L, Instant.now()))
				.getId();
		values.save(new ListingAxisValueEntity(listingId, axis1, "92%"));
		// axis2는 승격 안 함 — 빈칸으로 나와야 한다(체크리스트).
		notes.save(new ListingNoteEntity(listingId, "잔기스 있음"));

		ComparisonView view = useCase.get(productId);

		assertThat(view.axes()).extracting("name").containsExactly("배터리%", "구성");
		ComparisonRow row = view.rows().get(0);
		assertThat(row.title()).isEqualTo("아이폰 17 256");
		assertThat(row.axisValues().get(axis1)).isEqualTo("92%");
		assertThat(row.axisValues()).doesNotContainKey(axis2); // 빈칸은 키 자체가 없다 — null 값이 아니라
		assertThat(row.notes()).containsExactly("잔기스 있음");
	}

	@Test
	void emptyComparisonForAProductWithNoListingsOrAxes() {
		ComparisonView view = useCase.get(productId);

		assertThat(view.axes()).isEmpty();
		assertThat(view.rows()).isEmpty();
	}

	@Test
	void soldListingsAreExcludedFromTheComparison() {
		listings.save(new ListingEntity(searchId, "a1", "팔린 것", 700_000L, Instant.now())).disappeared();
		listings.save(new ListingEntity(searchId, "a2", "살아있는 것", 750_000L, Instant.now()));

		ComparisonView view = useCase.get(productId);

		assertThat(view.rows()).extracting(ComparisonRow::title).containsExactly("살아있는 것");
	}
}
