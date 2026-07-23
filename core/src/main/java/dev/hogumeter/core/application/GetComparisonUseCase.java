package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ComparisonAxisEntity;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueEntity;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueRepository;
import dev.hogumeter.core.adapter.persistence.ListingEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteRepository;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.domain.used.ListingStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-05 AC-18 — 병렬 비교표 데이터 조립(read-model, compute-on-demand). 통계 가공은 없다: 축 정의 +
 * 매물별 값 매트릭스 + 메모 전문을 그대로 낸다. 승격 안 된 축은 <b>키 자체를 비운다</b> — null 값이 아니라
 * "키 없음"이어야 web이 "빈칸=미확인"과 "명시적으로 빈 문자열 값"을 구별할 수 있다.
 *
 * <p>제품이 아니라 <b>등록된 조건검색들의 매물</b>을 모은다 — {@code listing}은 검색 단위로 쌓이고
 * 제품 단위 링크가 없다(product_id는 used_search에만 있다). 소실(SOLD/REMOVED)된 매물은 비교표에서
 * 뺀다 — 이미 없는 매물과 지금 있는 매물을 나란히 비교하면 혼란만 준다.
 */
@Service
public class GetComparisonUseCase {

	private final UsedSearchRepository searches;
	private final ListingRepository listings;
	private final ComparisonAxisRepository axes;
	private final ListingAxisValueRepository axisValues;
	private final ListingNoteRepository notes;

	public GetComparisonUseCase(UsedSearchRepository searches, ListingRepository listings,
			ComparisonAxisRepository axes, ListingAxisValueRepository axisValues, ListingNoteRepository notes) {
		this.searches = searches;
		this.listings = listings;
		this.axes = axes;
		this.axisValues = axisValues;
		this.notes = notes;
	}

	@Transactional(readOnly = true)
	public ComparisonView get(long productId) {
		List<ComparisonAxisEntity> productAxes = axes.findByProductId(productId);
		List<ListingEntity> activeListings = searches.findByProductId(productId).stream()
				.map(UsedSearchEntity::getId)
				.flatMap(searchId -> listings.findByUsedSearchId(searchId).stream())
				.filter(l -> l.getStatus() == ListingStatus.ACTIVE)
				.toList();

		List<ComparisonRow> rows = activeListings.stream().map(this::toRow).toList();
		return new ComparisonView(productAxes, rows);
	}

	private ComparisonRow toRow(ListingEntity listing) {
		Map<Long, String> axisValueMap = new HashMap<>();
		for (ListingAxisValueEntity v : axisValues.findByListingId(listing.getId())) {
			axisValueMap.put(v.getAxisId(), v.getValue());
		}
		List<String> noteBodies = notes.findByListingIdOrderByCreatedAt(listing.getId()).stream()
				.map(ListingNoteEntity::getBody)
				.toList();
		return new ComparisonRow(listing.getId(), listing.getTitle(), listing.getPrice(), listing.getUrl(),
				axisValueMap, noteBodies);
	}

	public record ComparisonView(List<ComparisonAxisEntity> axes, List<ComparisonRow> rows) {
	}

	/** 축값이 없는 축은 맵에서 빠진다 — "미확인"과 "빈 문자열"을 혼동하지 않게. */
	public record ComparisonRow(long listingId, String title, long price, String url,
			Map<Long, String> axisValues, List<String> notes) {
	}
}
