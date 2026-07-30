package dev.hogumeter.core.domain.alert;

/**
 * AL-03 후속 알림 자격(순수 도메인). 후속(검증·가격변화·종료·부활)은 "이미 알림이 나갔던 딜"에만 발송한다
 * — 처음부터 알림 대상이 아니었던 딜은 전이해도 후속을 만들지 않는다.
 *
 * <p><b>{@link FollowUpKind#PINNED_PRICE_INCREASED}만 예외다</b>(Q-83 ④, 2026-07-30 확정) — 핀(WatchItem
 * ACTIVE) 자체가 자격이라 첫 알림 여부와 무관하게 항상 허용한다. 목표가 미달로 첫 알림이 안 갔던 딜이라도
 * 사람이 지켜보고 있다면 가격 인상은 알려야 한다.
 */
public class FollowUpEvaluator {

	public boolean shouldSendFollowUp(FollowUpKind kind, boolean alreadyAlerted) {
		return kind == FollowUpKind.PINNED_PRICE_INCREASED || alreadyAlerted;
	}
}
