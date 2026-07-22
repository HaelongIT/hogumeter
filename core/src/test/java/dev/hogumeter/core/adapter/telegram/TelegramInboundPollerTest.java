package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.adapter.telegram.TelegramInboundApi.CallbackUpdate;
import dev.hogumeter.core.application.IgnoreDealUseCase;
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
		final List<String> edited = new ArrayList<>();
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
		public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
			answered.add(callbackQueryId + "|" + text + "|alert=" + showAlert);
		}

		@Override
		public void editMessageText(String chatId, long messageId, String text) {
			edited.add(chatId + "|" + messageId + "|" + text);
		}
	}

	private static final class NoOpIgnore extends IgnoreDealUseCase {
		NoOpIgnore() {
			super(null, null, null, null, null, null, null);
		}

		@Override
		public void ignore(long dealEventId) {
			// 이 폴러 테스트는 promote 경로만 본다 — ignore는 no-op
		}
	}

	private final RecordingResolve resolve = new RecordingResolve();
	private final ReviewCallbackRouter router = new ReviewCallbackRouter(resolve, new NoOpIgnore(), "555", "");
	private final FakeInbound api = new FakeInbound();
	private final TelegramInboundPoller poller = new TelegramInboundPoller(api, router);

	@Test
	void routesCallbackAnswersEditsMessageAndAdvancesOffset() {
		api.next = List.of(new CallbackUpdate(100, 555, "promote:42", "q1", 555L, 9001L, "아이폰 특가 알림"));

		poller.poll();

		assertThat(resolve.calls).containsExactly("promote:42:TELEGRAM");
		assertThat(api.answered).hasSize(1);
		// 결과를 모달(show_alert=true)로 답한다 — 일시 토스트는 놓치기 쉽다(Q-73 ①)
		assertThat(api.answered.get(0)).startsWith("q1|").endsWith("|alert=true");
		// 성공 → 원 메시지를 편집(버튼 제거 + 원문 아래 결과 덧붙임) — 나중에 봐도 처리됐음을 안다(Q-73 ③)
		assertThat(api.edited).hasSize(1);
		assertThat(api.edited.get(0)).contains("|9001|").contains("아이폰 특가 알림").contains("승격");

		// 다음 폴은 처리한 것 다음(101)부터 — 재수신 방지
		api.next = List.of();
		poller.poll();
		assertThat(api.lastPolledOffset).isEqualTo(101);
	}

	/** 상태가 안 바뀐 콜백(권한 없음 등)은 답만 하고 <b>원 메시지를 편집하지 않는다</b> — 남의 메시지를 안 건드린다(Q-73 ③). */
	@Test
	void doesNotEditMessageWhenActionNotApplied() {
		api.next = List.of(new CallbackUpdate(100, 999, "promote:42", "q1", 999L, 9001L, "알림")); // 999=허용 밖

		poller.poll();

		assertThat(resolve.calls).isEmpty(); // 처리 안 함
		assertThat(api.answered).hasSize(1); // "권한 없음" 답은 한다
		assertThat(api.edited).isEmpty(); // 편집은 안 한다
	}

	@Test
	void doesNotThrowWhenPollFails() {
		api.toThrow = new RuntimeException("network");

		assertThatCode(poller::poll).doesNotThrowAnyException();
	}
}
