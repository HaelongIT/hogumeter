package dev.hogumeter.core.domain.used;

/**
 * 중고 매물 생애주기 상태(USED-02, docs/used/02). ACTIVE에서만 SOLD(목록 소실 = 판매완료 추정)·REMOVED로
 * 전이하고, SOLD·REMOVED는 종착이다. 목록 소실을 SOLD로 추정하는 것은 번개 status 코드표 미실측(Q-44)이라
 * 잠정임에 유의 — 판정 근거는 소비 층이 표시한다.
 */
public enum ListingStatus {
	ACTIVE,
	SOLD,
	REMOVED;

	public boolean canTransitionTo(ListingStatus target) {
		return this == ACTIVE && (target == SOLD || target == REMOVED);
	}
}
