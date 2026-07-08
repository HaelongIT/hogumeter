package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1 alert_policy 테이블 — variant별 알림 정책. k_display·exclude_keywords·demand_axis_filter는
 * 기본값/nullable이라 이 슬라이스에서 미매핑. quiet_hours는 시(0~23) smallint.
 */
@Entity
@Table(name = "alert_policy")
public class AlertPolicyEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	@Column(name = "target_price")
	private Long targetPrice;

	@Column(name = "period_months", nullable = false)
	private int periodMonths;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "quiet_hours_start")
	private Integer quietHoursStart;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "quiet_hours_end")
	private Integer quietHoursEnd;

	protected AlertPolicyEntity() {
	}

	public AlertPolicyEntity(Long variantId, Long targetPrice, int periodMonths,
			Integer quietHoursStart, Integer quietHoursEnd) {
		this.variantId = variantId;
		this.targetPrice = targetPrice;
		this.periodMonths = periodMonths;
		this.quietHoursStart = quietHoursStart;
		this.quietHoursEnd = quietHoursEnd;
	}

	public Long getVariantId() {
		return variantId;
	}

	public Long getTargetPrice() {
		return targetPrice;
	}

	public int getPeriodMonths() {
		return periodMonths;
	}

	public Integer getQuietHoursStart() {
		return quietHoursStart;
	}

	public Integer getQuietHoursEnd() {
		return quietHoursEnd;
	}
}
