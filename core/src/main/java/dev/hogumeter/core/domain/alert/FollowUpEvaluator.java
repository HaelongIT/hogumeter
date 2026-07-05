package dev.hogumeter.core.domain.alert;

/**
 * AL-03 후속 알림 자격(순수 도메인). 후속(검증·가격변화·종료)은 "이미 알림이 나갔던 딜"에만 발송한다
 * — 처음부터 알림 대상이 아니었던 딜은 전이해도 후속을 만들지 않는다.
 */
public class FollowUpEvaluator {

	public boolean shouldSendFollowUp(FollowUpKind kind, boolean alreadyAlerted) {
		return alreadyAlerted;
	}
}
