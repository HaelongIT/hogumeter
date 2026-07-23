package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** V3 {@code comparison_axis}(USED-05 AC-17). 비교축은 <b>제품 단위</b>로 정의한다 — 매물 단위가 아니다. */
@Entity
@Table(name = "comparison_axis")
public class ComparisonAxisEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private String name;

	protected ComparisonAxisEntity() {
	}

	public ComparisonAxisEntity(Long productId, String name) {
		this.productId = productId;
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getName() {
		return name;
	}
}
