package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * V3 {@code listing_axis_value}(USED-05 AC-17). 메모 값을 축으로 <b>승격</b>한 결과 —
 * 승격은 명시적 사용자 행위다(자동 추출 아님, 그건 Phase 2/LLM 자리).
 */
@Entity
@Table(name = "listing_axis_value")
public class ListingAxisValueEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "listing_id", nullable = false)
	private Long listingId;

	@Column(name = "axis_id", nullable = false)
	private Long axisId;

	@Column(nullable = false)
	private String value;

	protected ListingAxisValueEntity() {
	}

	public ListingAxisValueEntity(Long listingId, Long axisId, String value) {
		this.listingId = listingId;
		this.axisId = axisId;
		this.value = value;
	}

	public Long getId() {
		return id;
	}

	public Long getListingId() {
		return listingId;
	}

	public Long getAxisId() {
		return axisId;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
