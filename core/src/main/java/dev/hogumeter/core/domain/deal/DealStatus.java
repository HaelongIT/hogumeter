package dev.hogumeter.core.domain.deal;

import java.util.Map;
import java.util.Set;

/**
 * DealEvent 상태기계(docs/02-domain-model.md). 허용 전이만 통과, 나머지는 거부(BM-04 AC-6).
 * NEW→ACTIVE(수집) / ACTIVE→VERIFIED(2번째 사이트) / ACTIVE·VERIFIED→ENDED(품절·삭제·종료).
 * PRICE_CHANGED는 상태가 아니라 이벤트(상태 불변)라 여기 없다.
 */
public enum DealStatus {
	NEW,
	ACTIVE,
	VERIFIED,
	ENDED;

	private static final Map<DealStatus, Set<DealStatus>> ALLOWED = Map.of(
			NEW, Set.of(ACTIVE),
			ACTIVE, Set.of(VERIFIED, ENDED),
			VERIFIED, Set.of(ENDED),
			ENDED, Set.of());

	public boolean canTransitionTo(DealStatus target) {
		return ALLOWED.get(this).contains(target);
	}

	/** 허용 전이면 target 반환, 아니면 거부. */
	public DealStatus transitionTo(DealStatus target) {
		if (!canTransitionTo(target)) {
			throw new IllegalDealTransitionException(this, target);
		}
		return target;
	}

	/** raw_deal_post.status(문자열)의 "종료" 집합 — 이 판정의 정본(사본 금지, Reprocess도 이걸 참조). */
	public static final Set<String> ENDED_RAW_STATUSES = Set.of("SOLD_OUT", "DELETED");

	/** 최초 수집된 원문의 status로 초기 딜 상태를 정한다(Q-27③). 종료 문자열이면 ENDED, 그 외·null이면 ACTIVE. */
	public static DealStatus fromRawPostStatus(String rawStatus) {
		return rawStatus != null && ENDED_RAW_STATUSES.contains(rawStatus) ? ENDED : ACTIVE; // Set.of.contains(null)=NPE 회피
	}
}
