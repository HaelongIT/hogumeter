package dev.hogumeter.core.domain.alert;

/** AL-03 후속 알림 종류. VERIFIED(N개 사이트 검증) / PRICE_CHANGED(본문 가격 변화) / ENDED(품절·종료). */
public enum FollowUpKind {
	VERIFIED,
	PRICE_CHANGED,
	ENDED
}
