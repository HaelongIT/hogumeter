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
		var result = router.route(555L, "promote:42");

		assertThat(resolve.calls).containsExactly("promote:42:TELEGRAM");
		assertThat(result.reply()).contains("승격");
		assertThat(result.editMessage()).as("상태 바뀜 → 메시지 편집(버튼 제거)").isTrue();
	}

	@Test
	void rejectsFromAllowedChatWithTelegramChannel() {
		var result = router.route(555L, "reject:42");

		assertThat(resolve.calls).containsExactly("reject:42:TELEGRAM");
		assertThat(result.reply()).contains("기각");
		assertThat(result.editMessage()).isTrue();
	}

	/** Q-22: [무시] 콜백은 사후학습으로 흐른다(허용 chat만). */
	@Test
	void ignoreFromAllowedChatRoutesToLearning() {
		var result = router.route(555L, "ignore:42");

		assertThat(ignoreDeal.ignored).containsExactly(42L);
		assertThat(result.reply()).contains("무시");
		assertThat(result.editMessage()).isTrue();
	}

	/** SEC-03: 허용 목록 밖 chat의 명령은 처리하지 않는다 — use-case를 아예 안 부른다. 남의 메시지도 안 건드린다. */
	@Test
	void deniesCommandFromUnauthorizedChat() {
		var result = router.route(999L, "promote:42");

		assertThat(resolve.calls).isEmpty();
		assertThat(result.reply()).contains("권한");
		assertThat(result.editMessage()).as("바뀐 게 없으니 편집 안 함").isFalse();
	}

	@Test
	void reportsAlreadyResolvedItemWithoutThrowing() {
		resolve.toThrow = new ReviewItemNotFoundException(42);

		var result = router.route(555L, "reject:42");

		assertThat(result.reply()).contains("이미 처리");
		assertThat(result.editMessage()).as("이 눌림으로 바뀐 게 없다").isFalse();
	}

	@Test
	void reportsUnclassifiedPromoteRejected() {
		resolve.toThrow = new UnclassifiedPromoteNotSupportedException(42);

		var result = router.route(555L, "promote:42");

		assertThat(result.reply()).contains("미상");
		assertThat(result.editMessage()).as("승격 실패 → reject 버튼은 남겨야 하므로 편집 안 함").isFalse();
	}

	@Test
	void unknownCallbackDataIsRejected() {
		assertThat(router.route(555L, "garbage").reply()).contains("알 수 없는");
		assertThat(router.route(555L, "promote:notanumber").reply()).contains("알 수 없는");
		assertThat(resolve.calls).isEmpty();
	}
}
