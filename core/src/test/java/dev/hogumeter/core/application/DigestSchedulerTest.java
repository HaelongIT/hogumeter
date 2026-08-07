package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.adapter.scheduler.DigestScheduler;
import dev.hogumeter.core.application.SendDigestUseCase.DigestSendReport;
import dev.hogumeter.core.application.port.out.AdminNotifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * BE-21(코드리뷰 20260806) — {@code PipelineScheduler}는 각 단계를 {@code runStep}으로 감싸 실패를
 * 격리·집계하지만, {@code DigestScheduler.tick()}은 {@code sendDigest.send()}가 던지는
 * {@code RuntimeException}을 전혀 안 잡았다. Spring 기본 {@code LoggingErrorHandler}만 로그를
 * 남기고 이 클래스 자신의 로거·{@link AdminNotifier}에는 기록이 없어, 다이제스트 조립 중 DB 일시
 * 장애가 나면 그 주 다이제스트가 조용히 안 나가고 다음 주까지 아무도 모른다.
 *
 * <p>패키지 프라이빗 테스트 seam 생성자({@code SendDigestUseCase(...)})에 접근하기 위해 이 테스트는
 * {@code application} 패키지에 둔다({@code SendDigestUseCaseTest}와 같은 이유).
 */
class DigestSchedulerTest {

	@Test
	void tickDoesNotPropagateWhenSendThrows() {
		RecordingAdminNotifier notifier = new RecordingAdminNotifier();
		DigestScheduler scheduler = new DigestScheduler(throwingSendDigest(), notifier);

		// 예전엔 여기서 RuntimeException이 그대로 던져져 스케줄러 스레드까지 전파됐다.
		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(notifier.messages).as("실패를 관리 알림으로 남긴다(OBS-03)").isNotEmpty();
	}

	@Test
	void tickDoesNotNotifyWhenSendSucceeds() {
		RecordingAdminNotifier notifier = new RecordingAdminNotifier();
		DigestScheduler scheduler = new DigestScheduler(succeedingSendDigest(), notifier);

		scheduler.tick();

		assertThat(notifier.messages).isEmpty();
	}

	/** 생성자 인자는 이 테스트에서 안 쓰이므로(오버라이드가 가로챈다) 전부 null로 넘긴다. */
	private static SendDigestUseCase throwingSendDigest() {
		return new SendDigestUseCase(null, null, null, null, null, null, null, null, 100) {
			@Override
			public DigestSendReport send() {
				throw new RuntimeException("DB 일시 장애(재현)");
			}
		};
	}

	private static SendDigestUseCase succeedingSendDigest() {
		return new SendDigestUseCase(null, null, null, null, null, null, null, null, 100) {
			@Override
			public DigestSendReport send() {
				return new DigestSendReport(1, 1, true);
			}
		};
	}

	private static final class RecordingAdminNotifier implements AdminNotifier {
		final List<String> messages = new ArrayList<>();

		@Override
		public void notify(String message) {
			messages.add(message);
		}
	}
}
