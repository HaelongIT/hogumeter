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
 * <p>{@code promoted}는 USED-03 알림 층이 세운다 — 첫 알림이 나간 매물만 후속(가격하락·판매완료)
 * 알림 대상이 된다(AC-8·9). {@code detail_fetched}는 아직 USED-04(상세 1회 fetch)의 몫이라 기본값이다.
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

	/** 원문 링크(V13). 관측에서 옮겨온다 — core가 플랫폼별 URL을 조립하지 않는다. NULL 가능. */
	@Column
	private String url;

	protected ListingEntity() {
	}

	public ListingEntity(Long usedSearchId, String listingId, String title, long price, Instant seenAt) {
		this(usedSearchId, listingId, title, price, seenAt, null);
	}

	public ListingEntity(Long usedSearchId, String listingId, String title, long price, Instant seenAt,
			String url) {
		this.usedSearchId = usedSearchId;
		this.listingId = listingId;
		this.title = title;
		this.price = price;
		this.url = url;
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
	 * 최신 관측이 URL을 알고 있으면 채운다. <b>있던 URL을 null로 덮지 않는다</b> — V13 이전 관측이
	 * 섞여 있으면 "모름"이 "없음"을 이기게 되고, 링크가 조용히 사라진다.
	 */
	public void observedUrl(String observedUrl) {
		if (observedUrl != null && !observedUrl.isBlank()) {
			this.url = observedUrl;
		}
	}

	/**
	 * 이 매물로 첫 알림이 나갔다(AC-8·9의 전제). 후속 알림(가격하락·판매완료)은 <b>승격된 매물만</b>
	 * 대상이라, 이 표식이 없으면 스냅샷 전체가 알림 대상이 되어 스팸이 된다.
	 */
	public void promote() {
		this.promoted = true;
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

	public String getUrl() {
		return url;
	}
}
