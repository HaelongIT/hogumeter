package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** V1 alias_dictionary 테이블 JPA 엔티티. product_id null = 전역 별칭. */
@Entity
@Table(name = "alias_dictionary")
public class AliasEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id")
	private Long productId;

	@Column(nullable = false)
	private String alias;

	protected AliasEntity() {
	}

	public AliasEntity(Long productId, String alias) {
		this.productId = productId;
		this.alias = alias;
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public String getAlias() {
		return alias;
	}
}
