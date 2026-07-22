package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V3 used_search 테이블 JPA 엔티티(USED-01). required/exclude는 Postgres text[]. created_at은 DB default. */
@Entity
@Table(name = "used_search")
public class UsedSearchEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private String platform;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "required_keywords", nullable = false)
	private List<String> requiredKeywords;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "exclude_keywords", nullable = false)
	private List<String> excludeKeywords;

	@Column(name = "target_price")
	private Long targetPrice;

	@Column(name = "poll_interval_min", nullable = false)
	private int pollIntervalMin;

	/**
	 * 이 검색의 목록 스냅샷을 이 시각(포함)까지 {@code listing}에 접었다(V11, USED-02).
	 * {@code null} = 아직 한 배치도 접지 않았다 — 진짜 상태이므로 기본값을 두지 않는다.
	 */
	@Column(name = "listings_folded_through")
	private Instant listingsFoldedThrough;

	protected UsedSearchEntity() {
	}

	public UsedSearchEntity(Long productId, String platform, List<String> requiredKeywords,
			List<String> excludeKeywords, Long targetPrice, int pollIntervalMin) {
		this.productId = productId;
		this.platform = platform;
		this.requiredKeywords = requiredKeywords;
		this.excludeKeywords = excludeKeywords;
		this.targetPrice = targetPrice;
		this.pollIntervalMin = pollIntervalMin;
	}

	public Long getId() {
		return id;
	}

	public Instant getListingsFoldedThrough() {
		return listingsFoldedThrough;
	}

	/** 배치를 접은 뒤 그 시각을 적는다 — 파생값으로 추측하지 않는다(소실만 있는 배치는 파생값을 못 민다). */
	public void foldedThrough(Instant observedAt) {
		this.listingsFoldedThrough = observedAt;
	}

	public Long getProductId() {
		return productId;
	}

	public String getPlatform() {
		return platform;
	}

	public List<String> getRequiredKeywords() {
		return requiredKeywords;
	}

	public List<String> getExcludeKeywords() {
		return excludeKeywords;
	}

	public Long getTargetPrice() {
		return targetPrice;
	}

	public int getPollIntervalMin() {
		return pollIntervalMin;
	}
}
