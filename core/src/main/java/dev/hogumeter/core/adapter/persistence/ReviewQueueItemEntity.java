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

	/** 이 근거가 큐에 들어간 횟수(Q-27 ④). 1보다 크면 재처리 멱등이 없다는 증거다. */
	@Column(nullable = false)
	private int occurrences = 1;

	/** 마지막 재적재 시각(created_at = 최초). 그 구간이 결함의 나이다(web seenLine). */
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	/** 같은 근거를 한 행으로 접는 키(유형별). null이면 접지 않는다(옛 행 호환). */
	@Column(name = "dedup_key")
	private String dedupKey;

	protected ReviewQueueItemEntity() {
	}

	public ReviewQueueItemEntity(ReviewQueueType type, Map<String, Object> payload) {
		this(type, payload, null);
	}

	public ReviewQueueItemEntity(ReviewQueueType type, Map<String, Object> payload, String dedupKey) {
		this.type = type;
		this.payload = payload;
		this.dedupKey = dedupKey;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (lastSeenAt == null) {
			lastSeenAt = createdAt;
		}
	}

	/** 같은 근거가 또 들어왔다 — 새 행을 만드는 대신 세고 시각을 갱신한다(Q-27 ④). */
	public void recordRecurrence() {
		occurrences++;
		lastSeenAt = Instant.now();
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

	public int getOccurrences() {
		return occurrences;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public String getDedupKey() {
		return dedupKey;
	}
}
