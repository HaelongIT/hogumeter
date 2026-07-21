package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * V8 held_alert — 방해금지로 보류된 첫 알림의 큐(AL-04/07, Q-20 ②). 방해금지가 끝난 틱에 이 딜을 <b>재평가</b>해
 * 보낸다(FlushHeldAlertsUseCase). 저장하는 것은 딜 참조뿐 — 본문·강도는 발송 시점에 현재 상태로 다시 판정한다
 * (AL-07 "발송 시점 재평가"). {@code variant_id}는 방해금지 종료 여부를 딜 로드 없이 판정하려고 둔다.
 * {@code (deal_event_id)} unique로 딜당 1건(멱등).
 */
@Entity
@Table(name = "held_alert")
public class HeldAlertEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_event_id", nullable = false)
	private Long dealEventId;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	protected HeldAlertEntity() {
	}

	public HeldAlertEntity(Long dealEventId, Long variantId) {
		this.dealEventId = dealEventId;
		this.variantId = variantId;
	}

	public Long getId() {
		return id;
	}

	public Long getDealEventId() {
		return dealEventId;
	}

	public Long getVariantId() {
		return variantId;
	}
}
