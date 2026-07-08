package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 발송 스텁 — 텔레그램 봇 토큰 미발급이라 실전송 대신 로그만(Q-20). 실 어댑터는 토큰 발급 후 교체.
 * 무중단 정지 조건(외부 실발송)을 회피하면서 파이프라인 배선을 완결한다.
 */
@Component
public class StubAlertSender implements AlertSender {

	private static final Logger log = LoggerFactory.getLogger(StubAlertSender.class);

	@Override
	public void send(AlertMessage message) {
		log.info("[STUB alert] intensity={} price={} labels={}",
				message.decision().intensity(), message.deal().priceFirst(), message.decision().labels());
	}
}
