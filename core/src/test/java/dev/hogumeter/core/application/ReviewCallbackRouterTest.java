package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 인라인 버튼 콜백 라우팅의 순수 계약: SEC-03(허용 chat만) · 파싱(promote/reject:id) · 채널 TELEGRAM 기록 ·
 * 실패를 문구로 돌려줌(안 던짐). 실 use-case를 기록용으로 오버라이드해 검증한다(이 프로젝트는 mock 대신 실객체).
 */
class ReviewCallbackRouterTest {

	private static final class RecordingResolve extends ResolveReviewItemUseCase {
		final List<String> calls = new ArrayList<>();
		RuntimeException toThrow;

		RecordingResolve() {
			super(null, null, null); // 생성자는 필드 대입만 — 오버라이드한 메서드는 deps를 안 쓴다
		}

		@Override
		public void promote(long id, String channel) {
			if (toThrow != null) {
				throw toThrow;
			}
			calls.add("promote:" + id + ":" + channel);
		}

		@Override
		public void reject(long id, String channel) {
			if (toThrow != null) {
				throw toThrow;
			}
			calls.add("reject:" + id + ":" + channel);
		}
	}

	private static final class RecordingIgnore extends IgnoreDealUseCase {
		final List<Long> ignored = new ArrayList<>();

		RecordingIgnore() {
			super(null, null, null, null, null, null, null);
		}

		@Override
		public void ignore(long dealEventId) {
			ignored.add(dealEventId);
		}
	}

	private final RecordingResolve resolve = new RecordingResolve();
	private final RecordingIgnore ignoreDeal = new RecordingIgnore();
	private final ReviewCallbackRouter router = new ReviewCallbackRouter(resolve, ignoreDeal, Set.of(555L));

	@Test
	void promotesFromAllowedChatWithTelegramChannel() {
		String reply = router.route(555L, "promote:42");

		assertThat(resolve.calls).containsExactly("promote:42:TELEGRAM");
		assertThat(reply).contains("승격");
	}

	@Test
	void rejectsFromAllowedChatWithTelegramChannel() {
		String reply = router.route(555L, "reject:42");

		assertThat(resolve.calls).containsExactly("reject:42:TELEGRAM");
		assertThat(reply).contains("기각");
	}

	/** Q-22: [무시] 콜백은 사후학습으로 흐른다(허용 chat만). */
	@Test
	void ignoreFromAllowedChatRoutesToLearning() {
		String reply = router.route(555L, "ignore:42");

		assertThat(ignoreDeal.ignored).containsExactly(42L);
		assertThat(reply).contains("무시");
	}

	/** SEC-03: 허용 목록 밖 chat의 명령은 처리하지 않는다 — use-case를 아예 안 부른다. */
	@Test
	void deniesCommandFromUnauthorizedChat() {
		String reply = router.route(999L, "promote:42");

		assertThat(resolve.calls).isEmpty();
		assertThat(reply).contains("권한");
	}

	@Test
	void reportsAlreadyResolvedItemWithoutThrowing() {
		resolve.toThrow = new ReviewItemNotFoundException(42);

		String reply = router.route(555L, "reject:42");

		assertThat(reply).contains("이미 처리");
	}

	@Test
	void reportsUnclassifiedPromoteRejected() {
		resolve.toThrow = new UnclassifiedPromoteNotSupportedException(42);

		String reply = router.route(555L, "promote:42");

		assertThat(reply).contains("미상");
	}

	@Test
	void unknownCallbackDataIsRejected() {
		assertThat(router.route(555L, "garbage")).contains("알 수 없는");
		assertThat(router.route(555L, "promote:notanumber")).contains("알 수 없는");
		assertThat(resolve.calls).isEmpty();
	}
}
