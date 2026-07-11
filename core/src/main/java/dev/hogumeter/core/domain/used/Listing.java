package dev.hogumeter.core.domain.used;

/**
 * 중고 매물 1건(USED-02). 목록 diff가 도출·갱신하는 생애주기 엔티티(순수 값). {@code listingId}는 자연키.
 *
 * <ul>
 *   <li>{@code promoted} — 알림이 나갔던 매물. 후속 알림(가격변동·판매완료)은 이 값이 true인 매물 한정(AC-8·9).</li>
 *   <li>{@code detailFetched} — 승격 시 1회 상세 fetch 완료 여부(AC-11).</li>
 * </ul>
 */
public record Listing(String listingId, String title, long price, ListingStatus status, boolean promoted,
		boolean detailFetched) {

	/** 목록에서 첫 관측된 매물 — ACTIVE·미승격·미fetch. */
	public static Listing observed(String listingId, String title, long price) {
		return new Listing(listingId, title, price, ListingStatus.ACTIVE, false, false);
	}

	public Listing markSold() {
		return transition(ListingStatus.SOLD);
	}

	public Listing markRemoved() {
		return transition(ListingStatus.REMOVED);
	}

	private Listing transition(ListingStatus target) {
		if (!status.canTransitionTo(target)) {
			throw new IllegalStateException("허용되지 않는 매물 전이: " + status + " → " + target);
		}
		return new Listing(listingId, title, price, target, promoted, detailFetched);
	}

	public Listing promote() {
		return new Listing(listingId, title, price, status, true, detailFetched);
	}

	public Listing withDetailFetched() {
		return new Listing(listingId, title, price, status, promoted, true);
	}

	/** AC-11: 승격됐고 아직 상세를 안 받은 매물만 1회 fetch 대상. */
	public boolean needsDetailFetch() {
		return promoted && !detailFetched;
	}
}
