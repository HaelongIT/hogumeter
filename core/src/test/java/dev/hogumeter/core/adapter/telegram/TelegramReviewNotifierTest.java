package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.adapter.telegram.TelegramApi.Button;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 미상 큐 알림의 버튼 계약: 승격 가능이면 [승격][기각], 아니면 [기각]만(core 400과 일치). 실패에 안 던진다. */
class TelegramReviewNotifierTest {

	private static final class FakeApi implements TelegramApi {
		String sentText;
		List<Button> sentButtons;
		boolean fail;

		@Override
		public int sendMessage(String chatId, String text) {
			return 200;
		}

		@Override
		public int sendMessage(String chatId, String text, List<Button> buttons) {
			if (fail) {
				throw new RuntimeException("transport");
			}
			this.sentText = text;
			this.sentButtons = buttons;
			return 200;
		}
	}

	@Test
	void promotableSendsPromoteAndRejectButtons() {
		FakeApi api = new FakeApi();

		new TelegramReviewNotifier(api, "555").notify(42, "🔍 이상치 의심", true);

		assertThat(api.sentButtons).extracting(Button::callbackData).containsExactly("promote:42", "reject:42");
		assertThat(api.sentText).contains("이상치");
	}

	@Test
	void nonPromotableSendsOnlyRejectButton() {
		FakeApi api = new FakeApi();

		new TelegramReviewNotifier(api, "555").notify(42, "🔍 미상 딜", false);

		assertThat(api.sentButtons).extracting(Button::callbackData).containsExactly("reject:42");
	}

	@Test
	void doesNotThrowWhenSendFails() {
		FakeApi api = new FakeApi();
		api.fail = true;

		assertThatCode(() -> new TelegramReviewNotifier(api, "555").notify(42, "x", true)).doesNotThrowAnyException();
	}
}
