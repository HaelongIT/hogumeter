package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.product.DemandAxisMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** V1 product 테이블 JPA 엔티티(등록). */
@Entity
@Table(name = "product")
public class ProductEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String category;

	@Column(name = "demand_axis_mode", nullable = false)
	@Enumerated(EnumType.STRING)
	private DemandAxisMode demandAxisMode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ProductEntity() {
	}

	public ProductEntity(String name, String category, DemandAxisMode demandAxisMode) {
		this.name = name;
		this.category = category;
		this.demandAxisMode = demandAxisMode;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getCategory() {
		return category;
	}

	public DemandAxisMode getDemandAxisMode() {
		return demandAxisMode;
	}
}
