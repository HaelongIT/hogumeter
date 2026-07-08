package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** V1 deal_event_source 테이블 — deal_event↔raw_deal_post 링크(교차검증 근거·대표 원문). */
@Entity
@Table(name = "deal_event_source")
public class DealEventSourceEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_event_id", nullable = false)
	private Long dealEventId;

	@Column(name = "raw_deal_post_id", nullable = false)
	private Long rawDealPostId;

	@Column(nullable = false)
	private String site;

	protected DealEventSourceEntity() {
	}

	public DealEventSourceEntity(Long dealEventId, Long rawDealPostId, String site) {
		this.dealEventId = dealEventId;
		this.rawDealPostId = rawDealPostId;
		this.site = site;
	}

	public Long getDealEventId() {
		return dealEventId;
	}

	public Long getRawDealPostId() {
		return rawDealPostId;
	}

	public String getSite() {
		return site;
	}
}
