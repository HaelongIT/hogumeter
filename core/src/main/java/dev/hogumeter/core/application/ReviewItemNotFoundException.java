package dev.hogumeter.core.application;

/**
 * 승격·기각할 <b>처리 대기(PENDING)</b> 항목이 없다(Q-15). 없는 id이거나 이미 처리된 항목이다 —
 * 둘 다 클라이언트 입장에선 "지금 큐에 없는 것"이라 같은 404다.
 */
public class ReviewItemNotFoundException extends RuntimeException {

	public static final String CODE = "REVIEW_ITEM_NOT_FOUND";

	public ReviewItemNotFoundException(long reviewItemId) {
		super("처리 대기 중인 미상 큐 항목이 없습니다: #" + reviewItemId);
	}
}
