package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 발송 스텁 — 텔레그램 봇 토큰 미발급이면 실전송 대신 <b>완성된 본문을 로그로</b> 남긴다(Q-20).
 * 실 어댑터({@code TelegramAlertSender})가 토큰이 있을 때 대체한다. 무중단 정지 조건(외부 실발송)을
 * 회피하면서 파이프라인 배선을 완결한다 — 본문 포맷터는 실 어댑터와 <b>공유</b>하므로, 스텁 로그가 곧
 * 실제로 나갈 메시지다(포맷이 조용히 갈라지지 않는다).
 */
@Component
public class StubAlertSender implements AlertSender {

	private static final Logger log = LoggerFactory.getLogger(StubAlertSender.class);

	private final AlertMessageFormatter formatter = new AlertMessageFormatter();

	@Override
	public void send(AlertMessage message) {
		// 여러 줄 본문을 한 로그 줄에 담아 형식이 콘솔 인코딩에 흔들리지 않게 한다(\n 이스케이프).
		log.info("[STUB alert] {}", formatter.format(message).replace("\n", " ⏎ "));
	}
}
