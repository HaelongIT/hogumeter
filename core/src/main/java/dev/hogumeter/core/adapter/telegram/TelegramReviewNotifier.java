package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.adapter.telegram.TelegramApi.Button;
import dev.hogumeter.core.application.port.out.ReviewNotifier;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 미상 큐 항목을 인라인 [승격][기각] 버튼과 함께 보낸다(Q-15). 누르면 {@code callback_data}("promote:{id}")가
 * 폴러→{@code ReviewCallbackRouter}로 흘러 실제로 처리된다 — 아웃바운드(버튼)와 인바운드(콜백)가 짝이다.
 * {@code promotable}이 아니면 [기각]만 그린다 — core가 400으로 막는 승격을 버튼으로도 그리지 않는다(과대약속 금지).
 * 실패에 던지지 않는다(수집 틱 보호).
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramReviewNotifier implements ReviewNotifier {

	private static final Logger log = LoggerFactory.getLogger(TelegramReviewNotifier.class);

	private final TelegramApi api;
	private final String chatId;

	@Autowired
	public TelegramReviewNotifier(@Value("${telegram.bot-token:}") String botToken,
			@Value("${telegram.chat-id:}") String chatId) {
		this(new HttpTelegramApi(botToken), chatId);
	}

	/** 테스트 seam. */
	TelegramReviewNotifier(TelegramApi api, String chatId) {
		this.api = api;
		this.chatId = chatId;
	}

	@Override
	public void notify(long reviewItemId, String summary, boolean promotable) {
		Button reject = new Button("🚫 기각", "reject:" + reviewItemId);
		List<Button> buttons = promotable
				? List.of(new Button("✅ 승격", "promote:" + reviewItemId), reject)
				: List.of(reject);
		try {
			api.sendMessage(chatId, summary, buttons);
		}
		catch (RuntimeException failure) {
			log.warn("미상 큐 알림 전송 실패 — 이 항목은 web 큐로 봐야 합니다", failure);
		}
	}
}
