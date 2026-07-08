package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.review.ReviewQueueType;
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
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1 review_queue_item 테이블 — BM-03/05/07이 만든 사람 판단 대기 항목. status·channel·resolved_at은
 * 기본값/nullable이라 미매핑(생성 시점엔 PENDING). 처리 UI는 기능3(알림) 소유.
 */
@Entity
@Table(name = "review_queue_item")
public class ReviewQueueItemEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ReviewQueueType type;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ReviewQueueItemEntity() {
	}

	public ReviewQueueItemEntity(ReviewQueueType type, Map<String, Object> payload) {
		this.type = type;
		this.payload = payload;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public ReviewQueueType getType() {
		return type;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}
}
