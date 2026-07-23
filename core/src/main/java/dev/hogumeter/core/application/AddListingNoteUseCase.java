package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ListingNoteEntity;
import dev.hogumeter.core.adapter.persistence.ListingNoteRepository;
import dev.hogumeter.core.adapter.persistence.ListingRepository;
import org.springframework.stereotype.Service;

/** USED-05 AC-16 — 자유 메모 추가. 구조를 강제하지 않는다(마찰 최소가 기본). */
@Service
public class AddListingNoteUseCase {

	private final ListingRepository listings;
	private final ListingNoteRepository notes;

	public AddListingNoteUseCase(ListingRepository listings, ListingNoteRepository notes) {
		this.listings = listings;
		this.notes = notes;
	}

	public long addNote(long listingId, String body) {
		if (!listings.existsById(listingId)) {
			throw new ListingNotFoundException(listingId);
		}
		return notes.save(new ListingNoteEntity(listingId, body)).getId();
	}
}
