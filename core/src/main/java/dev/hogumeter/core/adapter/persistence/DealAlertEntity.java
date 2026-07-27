package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V4 deal_alert — "무엇에 알림이 나갔나"(AL-03 후속 알림, Q-67). 후속은 FIRST가 있는 딜에만 보내고,
 * (deal_event_id, kind) unique로 종류별 1회만 발송한다(매 틱 도는 후속이 재발송하지 않게).
 * sent_at은 DB default now() — INSERT는 이 값을 안 채우고(우리 쓰기가 관여 안 함), DIGEST ④(부활
 * 이벤트가 이번 창 안인지)가 처음으로 읽어 read-only로 매핑했다(docs/91 Q-81).
 */
@Entity
@Table(name = "deal_alert")
public class DealAlertEntity {

	/** 첫(원) 알림. 후속 3종은 {@code FollowUpKind.name()}과 문자열이 일치한다. */
	public static final String FIRST = "FIRST";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_event_id", nullable = false)
	private Long dealEventId;

	@Column(nullable = false)
	private String kind;

	@Column(name = "sent_at", insertable = false, updatable = false)
	private Instant sentAt;

	protected DealAlertEntity() {
	}

	public DealAlertEntity(Long dealEventId, String kind) {
		this.dealEventId = dealEventId;
		this.kind = kind;
	}

	public Long getId() {
		return id;
	}

	public Long getDealEventId() {
		return dealEventId;
	}

	public String getKind() {
		return kind;
	}

	public Instant getSentAt() {
		return sentAt;
	}
}
