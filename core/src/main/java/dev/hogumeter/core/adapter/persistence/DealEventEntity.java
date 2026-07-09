package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.deal.DealStatus;
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
 * applied_conditions·confidence)은 미매핑(기본값·nullable).
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

	protected DealEventEntity() {
	}

	public DealEventEntity(Long variantId, boolean unclassified, List<Long> productCandidates,
			long priceFirst, long priceMin, long priceMax, long priceLast, Origin origin, boolean crossVerified,
			OutlierFlag outlierFlag, boolean permanentlyExcluded, DealStatus status,
			Instant firstSeen, Instant lastSeen) {
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

	/** 병합 결과(도메인 merge 산출)를 반영. 이상치 플래그·영구제외는 유지(C-4: 유입 1회 판정). */
	public void applyMerge(long priceFirst, long priceMin, long priceMax, long priceLast,
			boolean crossVerified, DealStatus status, Instant firstSeen, Instant lastSeen) {
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

	/** Q-27 상태변화 재처리 — status·lastSeen만 갱신(가격·이상치·firstSeen 불변). */
	public void applyStatusChange(DealStatus status, Instant lastSeen) {
		this.status = status;
		this.lastSeen = lastSeen;
	}
}
