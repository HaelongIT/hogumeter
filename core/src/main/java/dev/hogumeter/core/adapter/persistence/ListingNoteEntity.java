package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** V3 {@code listing_note}(USED-05 AC-16). 구조 강제 없는 자유 메모 — 글에 없는 관찰도 담는다. */
@Entity
@Table(name = "listing_note")
public class ListingNoteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "listing_id", nullable = false)
	private Long listingId;

	@Column(nullable = false)
	private String body;

	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	protected ListingNoteEntity() {
	}

	public ListingNoteEntity(Long listingId, String body) {
		this.listingId = listingId;
		this.body = body;
	}

	public Long getId() {
		return id;
	}

	public Long getListingId() {
		return listingId;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
