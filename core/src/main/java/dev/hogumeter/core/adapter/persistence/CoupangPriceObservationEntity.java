package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V14 {@code coupang_price_observation}(CMP-02). insert-only — 확장이 쿠팡 페이지를 읽을 때마다
 * 하나씩 쌓인다(가격 이력 부산물, docs/90). core는 쓰지 않고 받기만 한다.
 */
@Entity
@Table(name = "coupang_price_observation")
public class CoupangPriceObservationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "variant_id", nullable = false)
	private Long variantId;

	@Column(name = "regular_price", nullable = false)
	private long regularPrice;

	/** 와우회원가. null = 비회원이거나 미표시 — 0원이 아니다. */
	@Column(name = "wow_price")
	private Long wowPrice;

	/** null = 확장이 못 읽음(모름). 무료배송 확인 시 0. */
	@Column(name = "shipping_fee")
	private Long shippingFee;

	@Column(nullable = false)
	private String url;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	protected CoupangPriceObservationEntity() {
	}

	public CoupangPriceObservationEntity(Long variantId, long regularPrice, Long wowPrice, Long shippingFee,
			String url, Instant observedAt) {
		this.variantId = variantId;
		this.regularPrice = regularPrice;
		this.wowPrice = wowPrice;
		this.shippingFee = shippingFee;
		this.url = url;
		this.observedAt = observedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getVariantId() {
		return variantId;
	}

	public long getRegularPrice() {
		return regularPrice;
	}

	public Long getWowPrice() {
		return wowPrice;
	}

	public Long getShippingFee() {
		return shippingFee;
	}

	public String getUrl() {
		return url;
	}

	public Instant getObservedAt() {
		return observedAt;
	}
}
