package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.used.ListingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * V3 {@code listing} — 목록 diff가 도출·갱신하는 <b>생애주기 엔티티</b>(USED-02). 자연키는
 * {@code (used_search_id, listing_id)}이며 끌올(같은 매물 재노출)은 새 행이 아니라 같은 행의 갱신이다.
 *
 * <p>{@code promoted}·{@code detail_fetched}는 알림 승격 층(USED-03·04)의 몫이라 여기서는 기본값만
 * 지킨다 — 이 슬라이스는 생애주기만 접는다.
 */
@Entity
@Table(name = "listing")
public class ListingEntity {

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

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ListingStatus status;

	@Column(nullable = false)
	private boolean promoted;

	@Column(name = "detail_fetched", nullable = false)
	private boolean detailFetched;

	@Column(name = "first_seen", nullable = false)
	private Instant firstSeen;

	@Column(name = "last_seen", nullable = false)
	private Instant lastSeen;

	protected ListingEntity() {
	}

	public ListingEntity(Long usedSearchId, String listingId, String title, long price, Instant seenAt) {
		this.usedSearchId = usedSearchId;
		this.listingId = listingId;
		this.title = title;
		this.price = price;
		this.status = ListingStatus.ACTIVE;
		this.promoted = false;
		this.detailFetched = false;
		this.firstSeen = seenAt;
		this.lastSeen = seenAt;
	}

	/** 이번 스냅샷에도 있었다 — 가격·제목은 최신 관측을 따르고 관측 시각을 민다. */
	public void observedAgain(String title, long price, Instant seenAt) {
		this.title = title;
		this.price = price;
		this.lastSeen = seenAt;
	}

	/**
	 * 종착 상태였던 매물이 목록에 다시 나타났다(재등록·복구). 새 행을 만들 수 없다(자연키 UNIQUE) —
	 * 같은 행을 되살리고 {@code first_seen}은 <b>보존</b>한다. 처음 본 시각을 지어내지 않는다.
	 */
	public void revive(String title, long price, Instant seenAt) {
		this.status = ListingStatus.ACTIVE;
		observedAgain(title, price, seenAt);
	}

	/** 목록에서 사라졌다 = 판매완료 추정(AC-9). 잠정 판정임은 소비 층이 표시한다(Q-44). */
	public void disappeared() {
		if (status.canTransitionTo(ListingStatus.SOLD)) {
			this.status = ListingStatus.SOLD;
		}
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

	public ListingStatus getStatus() {
		return status;
	}

	public boolean isPromoted() {
		return promoted;
	}

	public boolean isDetailFetched() {
		return detailFetched;
	}

	public Instant getFirstSeen() {
		return firstSeen;
	}

	public Instant getLastSeen() {
		return lastSeen;
	}
}
