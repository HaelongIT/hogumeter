package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AdminNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 관리 알림 실 발송(OBS-03). {@code telegram.enabled=true}일 때만. 1인용이라 운영자=사용자이므로 딜 알림과
 * 같은 {@code TELEGRAM_CHAT_ID}로 보내되, 🔧 접두로 "딜이 아니라 시스템 알림"임을 표시한다. SEC-01: 토큰은
 * URL에만(로그 금지). 실패에 던지지 않는다 — 관리 알림이 못 갔다고 파이프라인을 멈추면 본말전도다.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramAdminNotifier implements AdminNotifier {

	private static final Logger log = LoggerFactory.getLogger(TelegramAdminNotifier.class);

	private final TelegramApi api;
	private final String chatId;

	@Autowired
	public TelegramAdminNotifier(@Value("${telegram.bot-token:}") String botToken,
			@Value("${telegram.chat-id:}") String chatId) {
		this(new HttpTelegramApi(botToken), chatId);
	}

	/** 테스트 seam — fake TelegramApi 주입. */
	TelegramAdminNotifier(TelegramApi api, String chatId) {
		this.api = api;
		this.chatId = chatId;
	}

	@Override
	public void notify(String message) {
		try {
			api.sendMessage(chatId, "🔧 [시스템] " + message);
		}
		catch (RuntimeException failure) {
			log.warn("관리 알림 전송 실패 — 이 알림은 못 갔습니다", failure);
		}
	}
}
