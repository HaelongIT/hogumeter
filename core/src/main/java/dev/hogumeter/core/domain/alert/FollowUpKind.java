package dev.hogumeter.core.domain.alert;

/**
 * AL-03 후속 알림 종류. VERIFIED(N개 사이트 검증) / PRICE_CHANGED(본문 가격 변화) / ENDED(품절·종료) /
 * <b>REOPENED(부활 — DN-C1)</b> / <b>PINNED_PRICE_INCREASED(핀 후속 인상 1회 — Q-83 ④)</b>.
 *
 * <p>REOPENED는 잠정 종료(ENDED)됐던 딜이 다시 관측돼 ACTIVE로 복귀한 사건이다. 확정본(docs/90 §6 v1.3)이
 * "재개는 <b>새 첫 알림이 아니라 후속 '부활'</b>"이라고 못박았다 — 첫 알림 경로로 보내면 이미 한 번 알렸던
 * 딜에 같은 알림이 다시 나간다(Q-13이 병합에서 잡았던 것과 같은 부류의 결함).
 *
 * <p><b>PINNED_PRICE_INCREASED</b>는 나머지 넷과 자격 조건이 다르다(2026-07-30 확정, decision-log) — 나머지는
 * "첫 알림이 나갔던 딜에만" 후속을 보내지만, 이건 <b>핀(WatchItem ACTIVE) 자체가 자격</b>이다. 목표가 미달 등
 * 이유로 첫 알림이 안 갔던 딜이라도 사람이 지켜보고 있다면 가격 인상은 알려야 한다({@link FollowUpEvaluator}).
 * "1회만"은 다른 종류와 같은 {@code (dealEventId, kind)} 유니크 이력으로 충분하다 — 새 메커니즘 불필요.
 */
public enum FollowUpKind {
	VERIFIED,
	PRICE_CHANGED,
	ENDED,
	REOPENED,
	PINNED_PRICE_INCREASED
}
