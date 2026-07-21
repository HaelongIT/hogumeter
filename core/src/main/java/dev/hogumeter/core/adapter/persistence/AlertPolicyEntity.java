package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1 alert_policy 테이블 — variant별 알림 정책. quiet_hours는 시(0~23) smallint.
 *
 * <p>{@code demand_axis_filter}는 아직 미매핑이다 — <b>소비처가 없어서</b>다(Q-66 수요축 값별 알림 필터).
 * 소비 기능과 함께 매핑한다. 매핑만 붙이면 저장되는데 아무도 안 쓰는 죽은 컬럼이 되고, 화면은 저장되는 줄 안다.
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

	/**
	 * 제외 키워드(Q-28, C-5). 제목이 여기 걸리는 딜은 <b>전 통계에서 제외</b>된다(리퍼·벌크 등을 신품 기준가에서
	 * 뺀다). 편집 가능한 손잡이라 저장 시점이 아니라 <b>조회 시점</b>에 딜 제목에 대고 판정한다({@code List<String>}).
	 */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "exclude_keywords", nullable = false)
	private List<String> excludeKeywords;

	protected AlertPolicyEntity() {
	}

	public AlertPolicyEntity(Long variantId, Long targetPrice, int periodMonths,
			Integer quietHoursStart, Integer quietHoursEnd, int kDisplay, List<String> excludeKeywords) {
		this.variantId = variantId;
		this.targetPrice = targetPrice;
		this.periodMonths = periodMonths;
		this.quietHoursStart = quietHoursStart;
		this.quietHoursEnd = quietHoursEnd;
		this.kDisplay = kDisplay;
		this.excludeKeywords = excludeKeywords;
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

	/** null = 미설정(빈 목록으로 읽는다). 제목 판정은 이 목록에 대고 한다(Q-28). */
	public List<String> getExcludeKeywords() {
		return excludeKeywords == null ? List.of() : excludeKeywords;
	}
}
