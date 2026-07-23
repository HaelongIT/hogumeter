package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteRepository;
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

/** USED-05 AC-16 — 자유 메모. 구조 강제 없음(마찰 최소). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AddListingNoteUseCaseTest {

	@Autowired
	AddListingNoteUseCase useCase;
	@Autowired
	ListingRepository listings;
	@Autowired
	ListingNoteRepository notes;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;

	private Long searchId;

	@BeforeEach
	void setUp() {
		ProductEntity product = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED));
		searchId = searches.save(new UsedSearchEntity(product.getId(), "BUNJANG", List.of("아이폰17"),
				List.of(), null, 10)).getId();
	}

	@Test
	void notePersistsAndIsReadableInOrder() {
		Long listingId = listings.save(new ListingEntity(searchId, "a1", "아이폰 17", 800_000L, Instant.now()))
				.getId();

		useCase.addNote(listingId, "잔기스 있음(사진엔 안 보임)");
		useCase.addNote(listingId, "판매자 응답 빠름");

		assertThat(notes.findByListingIdOrderByCreatedAt(listingId)).extracting(ListingNoteEntity::getBody)
				.containsExactly("잔기스 있음(사진엔 안 보임)", "판매자 응답 빠름");
	}

	@Test
	void unknownListingThrows() {
		assertThatThrownBy(() -> useCase.addNote(999_999L, "메모"))
				.isInstanceOf(ListingNotFoundException.class);
	}
}
