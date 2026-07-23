package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueEntity;
import dev.hogumeter.core.adapter.persistence.ListingAxisValueRepository;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * USED-05 AC-17 ② — 메모 값을 축으로 승격. 재승격(같은 매물·같은 축)은 값을 <b>갱신</b>한다 —
 * 재관찰로 값이 바뀔 수 있고, 유니크 제약(listing_id, axis_id)이 새 행을 막는다.
 */
@Service
public class PromoteAxisValueUseCase {

	private final ListingRepository listings;
	private final ComparisonAxisRepository axes;
	private final ListingAxisValueRepository values;

	public PromoteAxisValueUseCase(ListingRepository listings, ComparisonAxisRepository axes,
			ListingAxisValueRepository values) {
		this.listings = listings;
		this.axes = axes;
		this.values = values;
	}

	@Transactional
	public void promote(long listingId, long axisId, String value) {
		if (!listings.existsById(listingId)) {
			throw new ListingNotFoundException(listingId);
		}
		if (!axes.existsById(axisId)) {
			throw new ComparisonAxisNotFoundException(axisId);
		}
		values.findByListingIdAndAxisId(listingId, axisId)
				.ifPresentOrElse(
						existing -> existing.setValue(value),
						() -> values.save(new ListingAxisValueEntity(listingId, axisId, value)));
	}
}
