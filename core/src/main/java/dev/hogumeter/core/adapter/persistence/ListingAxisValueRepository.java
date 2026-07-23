package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingAxisValueRepository extends JpaRepository<ListingAxisValueEntity, Long> {

	List<ListingAxisValueEntity> findByListingId(Long listingId);

	/** 재승격(같은 매물·같은 축)은 값을 덮어쓴다 — 유니크 제약(listing_id, axis_id)과 정합. */
	Optional<ListingAxisValueEntity> findByListingIdAndAxisId(Long listingId, Long axisId);
}
