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
 * V1 alert_policy 테이블 — variant별 알림 정책. quiet_hours는 시(0~23) smallint.
 *
 * <p>{@code exclude_keywords}·{@code demand_axis_filter}는 아직 미매핑이다 — <b>소비처가 없어서</b>다
 * (Q-28 제외키워드 표본 적용 · Q-66 수요축). 소비 기능과 함께 매핑한다. 매핑만 붙이면 저장되는데 아무도
 * 안 쓰는 죽은 컬럼이 되고, 화면은 저장되는 줄 안다.
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

	/** 기준가 라벨 임계 K(3~10, DB CHECK). 사용자 손잡이 — 기준가 tier 판정이 이 값을 쓴다(Q-48 ①). */
	@Column(name = "k_display", nullable = false)
	private int kDisplay;

	protected AlertPolicyEntity() {
	}

	public AlertPolicyEntity(Long variantId, Long targetPrice, int periodMonths,
			Integer quietHoursStart, Integer quietHoursEnd, int kDisplay) {
		this.variantId = variantId;
		this.targetPrice = targetPrice;
		this.periodMonths = periodMonths;
		this.quietHoursStart = quietHoursStart;
		this.quietHoursEnd = quietHoursEnd;
		this.kDisplay = kDisplay;
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

	public int getKDisplay() {
		return kDisplay;
	}
}
