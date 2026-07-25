package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.watch.PinState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** V19 watch_item — WATCH(docs/17) 핀. 결말(BOUGHT·MISSED·DROPPED)은 종착, 재핀은 새 행. */
@Entity
@Table(name = "watch_item")
public class WatchItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_event_id", nullable = false)
	private Long dealEventId;

	@Column(name = "anchor_post_id")
	private Long anchorPostId;

	private String note;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PinState state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected WatchItemEntity() {
	}

	public WatchItemEntity(Long dealEventId, Long anchorPostId, String note) {
		this.dealEventId = dealEventId;
		this.anchorPostId = anchorPostId;
		this.note = note;
		this.state = PinState.ACTIVE;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	/** 결말 전이(순수 {@link PinState#transitionTo}가 허용을 강제) + 해소 시각 기록. */
	public void resolve(PinState target, Instant at) {
		this.state = state.transitionTo(target);
		this.resolvedAt = at;
	}

	public Long getId() {
		return id;
	}

	public Long getDealEventId() {
		return dealEventId;
	}

	public Long getAnchorPostId() {
		return anchorPostId;
	}

	public String getNote() {
		return note;
	}

	public PinState getState() {
		return state;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}
}
