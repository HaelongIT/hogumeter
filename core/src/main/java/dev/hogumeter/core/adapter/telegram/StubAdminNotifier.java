package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AdminNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 관리 알림 스텁(OBS-03) — 텔레그램 미설정(기본)이면 실전송 대신 로그로. {@code telegram.enabled=true}면
 * {@link TelegramAdminNotifier}가 대체한다. 딜 발송 스텁({@link StubAlertSender})과 같은 스위치를 탄다.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "false", matchIfMissing = true)
public class StubAdminNotifier implements AdminNotifier {

	private static final Logger log = LoggerFactory.getLogger(StubAdminNotifier.class);

	@Override
	public void notify(String message) {
		log.warn("[STUB admin] {}", message);
	}
}
