package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/** 관리 알림 실 발송: 설정된 chat로 🔧 접두를 붙여 보내고, 전송 실패에도 던지지 않는다(파이프라인 보호). */
class TelegramAdminNotifierTest {

	private static final class FakeApi implements TelegramApi {
		String sentChatId;
		String sentText;
		RuntimeException transport;

		@Override
		public int sendMessage(String chatId, String text) {
			if (transport != null) {
				throw transport;
			}
			this.sentChatId = chatId;
			this.sentText = text;
			return 200;
		}
	}

	@Test
	void sendsSystemPrefixedMessageToConfiguredChat() {
		FakeApi api = new FakeApi();
		new TelegramAdminNotifier(api, "555000").notify("파이프라인이 3틱 연속 실패했습니다.");

		assertThat(api.sentChatId).isEqualTo("555000");
		assertThat(api.sentText).contains("🔧").contains("파이프라인이 3틱 연속 실패했습니다.");
	}

	@Test
	void doesNotThrowWhenTransportFails() {
		FakeApi api = new FakeApi();
		api.transport = new RuntimeException("connection reset");

		assertThatCode(() -> new TelegramAdminNotifier(api, "555000").notify("x")).doesNotThrowAnyException();
	}
}
