package dev.hogumeter.core.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingNoteRepository extends JpaRepository<ListingNoteEntity, Long> {

	List<ListingNoteEntity> findByListingIdOrderByCreatedAt(Long listingId);
}
