package dev.hogumeter.core.domain.alert;

/**
 * AL-03 후속 알림 종류. VERIFIED(N개 사이트 검증) / PRICE_CHANGED(본문 가격 변화) / ENDED(품절·종료) /
 * <b>REOPENED(부활 — DN-C1)</b>.
 *
 * <p>REOPENED는 잠정 종료(ENDED)됐던 딜이 다시 관측돼 ACTIVE로 복귀한 사건이다. 확정본(docs/90 §6 v1.3)이
 * "재개는 <b>새 첫 알림이 아니라 후속 '부활'</b>"이라고 못박았다 — 첫 알림 경로로 보내면 이미 한 번 알렸던
 * 딜에 같은 알림이 다시 나간다(Q-13이 병합에서 잡았던 것과 같은 부류의 결함).
 */
public enum FollowUpKind {
	VERIFIED,
	PRICE_CHANGED,
	ENDED,
	REOPENED
}
