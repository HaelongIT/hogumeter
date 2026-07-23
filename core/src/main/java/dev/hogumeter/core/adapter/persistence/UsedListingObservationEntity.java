package dev.hogumeter.core.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V3 {@code used_listing_observation} — collector↔core <b>적재 계약 테이블</b>(USED-02). insert-only
 * 목록 스냅샷이다: collector가 검색 목록 한 페이지를 훑을 때마다 그 시점 매물 전부를 한 배치
 * (같은 {@code observed_at})로 넣고, core가 연속된 두 배치를 비교해 생애주기를 도출한다.
 *
 * <p>core는 <b>읽기만</b> 한다 — 그래서 {@code raw}(jsonb 크롤링 원본)는 매핑하지 않는다. 쓰지 않으므로
 * 미매핑 컬럼이 기본값으로 되돌아갈 위험이 없다(그 함정은 delete+insert 갱신에서 생긴다).
 */
@Entity
@Table(name = "used_listing_observation")
public class UsedListingObservationEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "used_search_id", nullable = false)
	private Long usedSearchId;

	@Column(name = "listing_id", nullable = false)
	private String listingId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private long price;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	/** 파서가 만든 원문 링크(V13). NULL 가능 — V13 이전 관측이거나 파서가 URL을 못 만든 경우. */
	@Column
	private String url;

	protected UsedListingObservationEntity() {
	}

	/** 테스트·시드용. 운영 삽입은 collector가 한다(core는 이 테이블을 읽기만 한다). */
	public UsedListingObservationEntity(Long usedSearchId, String listingId, String title, long price,
			Instant observedAt) {
		this(usedSearchId, listingId, title, price, observedAt, null);
	}

	public UsedListingObservationEntity(Long usedSearchId, String listingId, String title, long price,
			Instant observedAt, String url) {
		this.usedSearchId = usedSearchId;
		this.listingId = listingId;
		this.title = title;
		this.price = price;
		this.observedAt = observedAt;
		this.url = url;
	}

	public Long getId() {
		return id;
	}

	public Long getUsedSearchId() {
		return usedSearchId;
	}

	public String getListingId() {
		return listingId;
	}

	public String getTitle() {
		return title;
	}

	public long getPrice() {
		return price;
	}

	public Instant getObservedAt() {
		return observedAt;
	}

	public String getUrl() {
		return url;
	}
}
