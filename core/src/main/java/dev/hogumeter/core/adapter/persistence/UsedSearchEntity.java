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
