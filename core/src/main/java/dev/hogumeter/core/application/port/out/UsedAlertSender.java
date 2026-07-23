package dev.hogumeter.core.application.port.out;

/**
 * 중고 매물 알림 발송 아웃 포트(USED-03). {@link AlertSender}와 <b>같은 전송체가 함께 구현한다</b> —
 * 봇 토큰·chat_id는 하나이므로 빈을 둘로 나누면 설정이 둘로 갈린다. 포트를 나누는 이유는 전송이 아니라
 * <b>메시지의 부류</b>가 다르기 때문이다.
 */
public interface UsedAlertSender {

	void sendUsed(UsedAlertMessage message);
}
