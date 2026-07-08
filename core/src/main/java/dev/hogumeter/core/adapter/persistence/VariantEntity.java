package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V1 variant 테이블 JPA 엔티티. price_axis_values는 jsonb(가격축 이름→값). */
@Entity
@Table(name = "variant")
public class VariantEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "price_axis_values", nullable = false)
	private Map<String, String> priceAxisValues;

	@Column(nullable = false)
	private String label;

	protected VariantEntity() {
	}

	public VariantEntity(Long productId, String label, Map<String, String> priceAxisValues) {
		this.productId = productId;
		this.label = label;
		this.priceAxisValues = priceAxisValues;
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getLabel() {
		return label;
	}

	public Map<String, String> getPriceAxisValues() {
		return priceAxisValues;
	}
}
