package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * V4 deal_alert — "무엇에 알림이 나갔나"(AL-03 후속 알림, Q-67). 후속은 FIRST가 있는 딜에만 보내고,
 * (deal_event_id, kind) unique로 종류별 1회만 발송한다(매 틱 도는 후속이 재발송하지 않게).
 * sent_at은 DB default now() — 읽을 일이 없어 매핑하지 않는다.
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
}
