package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.deal.DealStatus;
import dev.hogumeter.core.domain.deal.IllegalDealTransitionException;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import dev.hogumeter.core.domain.deal.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * V1 deal_event 테이블 JPA 엔티티. 리치 도메인 {@code DealEvent}와의 임피던스는 {@link DealEventMapper}가 흡수
 * (sourceSites·대표 site/url은 deal_event_source에서 복원). 표본 산식에 불필요한 컬럼(shipping·base_price·
 * confidence)은 미매핑. applied_conditions는 읽기 전용 매핑(쓰기는 PreserveAppliedConditionsUseCase 네이티브 SQL 단독).
 */
@Entity
@Table(name = "deal_event")
public class DealEventEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "variant_id")
	private Long variantId;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "product_candidates")
	private List<Long> productCandidates;

	@Column(nullable = false)
	private boolean unclassified;

	@Column(name = "price_first", nullable = false)
	private long priceFirst;
	@Column(name = "price_min", nullable = false)
	private long priceMin;
	@Column(name = "price_max", nullable = false)
	private long priceMax;
	@Column(name = "price_last", nullable = false)
	private long priceLast;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Origin origin;

	@Column(name = "cross_verified", nullable = false)
	private boolean crossVerified;

	@Column(name = "outlier_flag", nullable = false)
	@Enumerated(EnumType.STRING)
	private OutlierFlag outlierFlag;

	@Column(name = "permanently_excluded", nullable = false)
	private boolean permanentlyExcluded;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private DealStatus status;

	@Column(name = "first_seen", nullable = false)
	private Instant firstSeen;
	@Column(name = "last_seen", nullable = false)
	private Instant lastSeen;

	/** BM-02 조건 태그 — 읽기 전용(insertable/updatable=false). 쓰기는 네이티브 SQL 단독. null = 태그 없음. */
	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "applied_conditions", insertable = false, updatable = false)
	private List<String> appliedConditions;

	/**
	 * 제목에서 판별한 수요축 값(Q-66 ①, V6). <b>null = 값 미상</b> — 수요축 없는 제품이거나, 제목에 값이
	 * 없거나, 둘 이상 보여 모르는 경우다. 조건 태그와 달리 <b>딜 생성 시 매칭이 정한다</b>(쓰기 가능).
	 */
	@Column(name = "demand_axis_value")
	private String demandAxisValue;

	protected DealEventEntity() {
	}

	public DealEventEntity(Long variantId, boolean unclassified, List<Long> productCandidates,
			long priceFirst, long priceMin, long priceMax, long priceLast, Origin origin, boolean crossVerified,
			OutlierFlag outlierFlag, boolean permanentlyExcluded, DealStatus status,
			Instant firstSeen, Instant lastSeen) {
		this(variantId, unclassified, productCandidates, priceFirst, priceMin, priceMax, priceLast, origin,
				crossVerified, outlierFlag, permanentlyExcluded, status, firstSeen, lastSeen, null);
	}

	public DealEventEntity(Long variantId, boolean unclassified, List<Long> productCandidates,
			long priceFirst, long priceMin, long priceMax, long priceLast, Origin origin, boolean crossVerified,
			OutlierFlag outlierFlag, boolean permanentlyExcluded, DealStatus status,
			Instant firstSeen, Instant lastSeen, String demandAxisValue) {
		this.variantId = variantId;
		this.unclassified = unclassified;
		this.productCandidates = productCandidates;
		this.priceFirst = priceFirst;
		this.priceMin = priceMin;
		this.priceMax = priceMax;
		this.priceLast = priceLast;
		this.origin = origin;
		this.crossVerified = crossVerified;
		this.outlierFlag = outlierFlag;
		this.permanentlyExcluded = permanentlyExcluded;
		this.status = status;
		this.firstSeen = firstSeen;
		this.lastSeen = lastSeen;
		this.demandAxisValue = demandAxisValue;
	}

	public Long getId() {
		return id;
	}

	public Long getVariantId() {
		return variantId;
	}

	public List<Long> getProductCandidates() {
		return productCandidates;
	}

	public boolean isUnclassified() {
		return unclassified;
	}

	public long getPriceFirst() {
		return priceFirst;
	}

	public long getPriceMin() {
		return priceMin;
	}

	public long getPriceMax() {
		return priceMax;
	}

	public long getPriceLast() {
		return priceLast;
	}

	public Origin getOrigin() {
		return origin;
	}

	public OutlierFlag getOutlierFlag() {
		return outlierFlag;
	}

	public boolean isPermanentlyExcluded() {
		return permanentlyExcluded;
	}

	public DealStatus getStatus() {
		return status;
	}

	public Instant getFirstSeen() {
		return firstSeen;
	}

	public Instant getLastSeen() {
		return lastSeen;
	}

	/** null = 태그 없음(빈 배열 아님, 값 없음을 값으로 쓰지 않는다). */
	public List<String> getAppliedConditions() {
		return appliedConditions;
	}

	/** null = 값 미상(Q-66 ①). SPLIT 제품에선 미상 딜이 기준가 표본에서 빠지고 사람이 분류한다. */
	public String getDemandAxisValue() {
		return demandAxisValue;
	}

	/**
	 * 병합 결과(도메인 merge 산출)를 반영. 이상치 플래그·영구제외는 유지(C-4: 유입 1회 판정).
	 *
	 * <p><b>상태 전이 안전망(Q-84)</b>: 이 메서드의 실제 호출자는 둘로 갈린다 — {@code IngestDealsUseCase}는
	 * {@link dev.hogumeter.core.domain.deal.DealMergePolicy}가 계산한 <b>진짜 전이</b>(예: 재개
	 * ENDED→ACTIVE, 교차검증 ACTIVE→VERIFIED)를 넘기고, {@code ReprocessDealPricesUseCase}는 가격만 갱신할
	 * 뿐이라 <b>같은 상태를 그대로</b> 되돌려 넣는다("종료 판정은 별도 유스케이스의 몫"). 그래서 같은
	 * 상태로의 호출은 허용하되, 실제로 값이 바뀌는 호출은 {@link DealStatus#canTransitionTo}를 지켜야 한다 —
	 * 우연히 옳았던 호출자들이 미래에 잘못된 전이를 만들면 조용히 상태가 깨지는 대신 즉시 던진다.
	 */
	public void applyMerge(long priceFirst, long priceMin, long priceMax, long priceLast,
			boolean crossVerified, DealStatus status, Instant firstSeen, Instant lastSeen) {
		if (status != this.status && !this.status.canTransitionTo(status)) {
			throw new IllegalDealTransitionException(this.status, status);
		}
		this.priceFirst = priceFirst;
		this.priceMin = priceMin;
		this.priceMax = priceMax;
		this.priceLast = priceLast;
		this.crossVerified = crossVerified;
		this.status = status;
		this.firstSeen = firstSeen;
		this.lastSeen = lastSeen;
	}

	public void setOutlierFlag(OutlierFlag outlierFlag) {
		this.outlierFlag = outlierFlag;
	}

	/** Q-15 기각 — 순수 도메인 {@code DealEvent.reject()}의 결과(영구 제외)를 반영한다. */
	public void setPermanentlyExcluded(boolean permanentlyExcluded) {
		this.permanentlyExcluded = permanentlyExcluded;
	}

	/**
	 * Q-27 상태변화 재처리 — status·lastSeen만 갱신(가격·이상치·firstSeen 불변).
	 *
	 * <p><b>상태 전이 안전망(Q-84)</b>: 이 메서드는 항상 실제 전이만 만든다(호출자가 이미 ACTIVE·VERIFIED
	 * 상태만 걸러 ENDED로 옮긴다) — {@code applyMerge}와 달리 같은 상태 재대입을 허용할 이유가 없어 항상
	 * {@link DealStatus#transitionTo}로 엄격히 검증한다.
	 */
	public void applyStatusChange(DealStatus status, Instant lastSeen) {
		this.status = this.status.transitionTo(status);
		this.lastSeen = lastSeen;
	}
}
