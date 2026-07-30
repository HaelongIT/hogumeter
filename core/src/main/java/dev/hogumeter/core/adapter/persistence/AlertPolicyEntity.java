package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1 alert_policy 테이블 — variant별 알림 정책. quiet_hours는 시(0~23) smallint.
 *
 * <p>{@code demand_axis_filter}는 2026-07-30 매핑(Q-48 ②) — 소비처(EvaluateAlertOnDealUseCase)와 생산자
 * (정책 패널)가 함께 생겼다. jsonb 컬럼이지만 값은 단순 문자열 배열이다(변형당 DEMAND축은 하나뿐이라
 * 축 이름을 함께 저장할 이유가 없다).
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

	/**
	 * Q-48 ② — 분리 제품에서 알림 받을 축값들. jsonb 저장, 빈 목록/null = 필터 없음.
	 *
	 * <p>필드가 {@code Map<String, Object>}인 이유(값은 {@code List<String>} 하나, 키 {@code "values"}):
	 * {@code exclude_keywords}(text[], {@code List<String>} + {@code SqlTypes.ARRAY})와 같은 제네릭
	 * 소거 타입에 {@code SqlTypes.JSON}을 또 붙이면 Hibernate가 스키마 검증 시 타입 기술자를 공유해
	 * exclude_keywords가 jsonb를 기대한다고 잘못 검증한다(실측, Boot 4.1/Hibernate 7). {@code
	 * ReviewQueueItemEntity.payload}가 이미 쓰는 {@code Map<String, Object>} + JSON 조합은 이 저장소에서
	 * 검증된 형태라 그대로 재사용한다 — 바깥 API(getter·생성자)는 여전히 {@code List<String>}이다.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "demand_axis_filter")
	private Map<String, Object> demandAxisFilter;

	private static final String DEMAND_AXIS_FILTER_KEY = "values";

	protected AlertPolicyEntity() {
	}

	public AlertPolicyEntity(Long variantId, Long targetPrice, int periodMonths,
			Integer quietHoursStart, Integer quietHoursEnd, int kDisplay, List<String> excludeKeywords,
			List<String> demandAxisFilter) {
		this.variantId = variantId;
		this.targetPrice = targetPrice;
		this.periodMonths = periodMonths;
		this.quietHoursStart = quietHoursStart;
		this.quietHoursEnd = quietHoursEnd;
		this.kDisplay = kDisplay;
		this.excludeKeywords = excludeKeywords;
		this.demandAxisFilter = toColumn(demandAxisFilter);
	}

	/** 벌크 UPDATE(JPQL)도 이 변환을 써야 한다 — 엔티티 밖에서 같은 컬럼 모양을 또 만들면 사본이 어긋난다. */
	public static Map<String, Object> toColumn(List<String> demandAxisFilter) {
		return (demandAxisFilter == null || demandAxisFilter.isEmpty()) ? null
				: Map.of(DEMAND_AXIS_FILTER_KEY, demandAxisFilter);
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

	/** null = 필터 없음(빈 목록으로 읽는다 — 전 축값 알림). */
	@SuppressWarnings("unchecked")
	public List<String> getDemandAxisFilter() {
		if (demandAxisFilter == null) {
			return List.of();
		}
		return (List<String>) demandAxisFilter.getOrDefault(DEMAND_AXIS_FILTER_KEY, List.of());
	}
}
