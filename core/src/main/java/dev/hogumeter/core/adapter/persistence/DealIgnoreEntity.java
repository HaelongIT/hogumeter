package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * V9 deal_ignore — 사용자가 [무시]한 딜(Q-22 사후학습). {@code title}은 학습 입력이라 무시 시점 값을 박제한다
 * (딜이 나중에 바뀌어도 그때 그 제목으로 배운다). {@code (deal_event_id)} unique로 딜당 1건(멱등).
 */
@Entity
@Table(name = "deal_ignore")
public class DealIgnoreEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_event_id", nullable = false)
	private Long dealEventId;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	@Column(nullable = false)
	private String title;

	protected DealIgnoreEntity() {
	}

	public DealIgnoreEntity(Long dealEventId, Long variantId, String title) {
		this.dealEventId = dealEventId;
		this.variantId = variantId;
		this.title = title;
	}

	public Long getVariantId() {
		return variantId;
	}

	public String getTitle() {
		return title;
	}
}
