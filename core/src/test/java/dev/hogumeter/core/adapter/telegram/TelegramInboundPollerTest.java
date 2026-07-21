package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.adapter.telegram.TelegramInboundApi.CallbackUpdate;
import dev.hogumeter.core.application.ResolveReviewItemUseCase;
import dev.hogumeter.core.application.ReviewCallbackRouter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 인바운드 폴러 계약: 콜백을 라우터로 넘기고, 답하고, offset을 전진시켜 재수신을 막는다. 한 폴의 실패에도
 * 던지지 않는다. 실 네트워크 없이 fake 인바운드 api + 기록용 use-case로 검증한다.
 */
class TelegramInboundPollerTest {

	private static final class RecordingResolve extends ResolveReviewItemUseCase {
		final List<String> calls = new ArrayList<>();

		RecordingResolve() {
			super(null, null, null);
		}

		@Override
		public void promote(long id, String channel) {
			calls.add("promote:" + id + ":" + channel);
		}

		@Override
		public void reject(long id, String channel) {
			calls.add("reject:" + id + ":" + channel);
		}
	}

	private static final class FakeInbound implements TelegramInboundApi {
		List<CallbackUpdate> next = new ArrayList<>();
		final List<String> answered = new ArrayList<>();
		long lastPolledOffset = -1;
		RuntimeException toThrow;

		@Override
		public List<CallbackUpdate> getUpdates(long offset) {
			lastPolledOffset = offset;
			if (toThrow != null) {
				throw toThrow;
			}
			return next;
		}

		@Override
		public void answerCallbackQuery(String callbackQueryId, String text) {
			answered.add(callbackQueryId + "|" + text);
		}
	}

	private final RecordingResolve resolve = new RecordingResolve();
	private final ReviewCallbackRouter router = new ReviewCallbackRouter(resolve, "555", ""); // 허용 chat 555
	private final FakeInbound api = new FakeInbound();
	private final TelegramInboundPoller poller = new TelegramInboundPoller(api, router);

	@Test
	void routesCallbackAnswersAndAdvancesOffset() {
		api.next = List.of(new CallbackUpdate(100, 555, "promote:42", "q1"));

		poller.poll();

		assertThat(resolve.calls).containsExactly("promote:42:TELEGRAM");
		assertThat(api.answered).hasSize(1);
		assertThat(api.answered.get(0)).startsWith("q1|");

		// 다음 폴은 처리한 것 다음(101)부터 — 재수신 방지
		api.next = List.of();
		poller.poll();
		assertThat(api.lastPolledOffset).isEqualTo(101);
	}

	@Test
	void doesNotThrowWhenPollFails() {
		api.toThrow = new RuntimeException("network");

		assertThatCode(poller::poll).doesNotThrowAnyException();
	}
}
