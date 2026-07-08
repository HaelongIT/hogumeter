package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.product.AxisType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** V1 product_axis 테이블 JPA 엔티티. allowed_values는 Postgres text[]. */
@Entity
@Table(name = "product_axis")
public class ProductAxisEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "axis_type", nullable = false)
	@Enumerated(EnumType.STRING)
	private AxisType axisType;

	@Column(nullable = false)
	private String name;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "allowed_values", nullable = false)
	private List<String> allowedValues;

	protected ProductAxisEntity() {
	}

	public ProductAxisEntity(Long productId, AxisType axisType, String name, List<String> allowedValues) {
		this.productId = productId;
		this.axisType = axisType;
		this.name = name;
		this.allowedValues = allowedValues;
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public AxisType getAxisType() {
		return axisType;
	}

	public String getName() {
		return name;
	}

	public List<String> getAllowedValues() {
		return allowedValues;
	}
}
