package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.SendDigestUseCase.DigestSendReport;
import dev.hogumeter.core.application.port.out.DigestSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * DIGEST 발송 배선 — 분할(DIG-02)이 실제로 {@link DigestSender}를 여러 번 부르는지, 부분 실패가
 * {@code allSucceeded}에 정직하게 반영되는지 검증한다(분할 계산 자체는 {@code DigestSplitterTest}가
 * 이미 순수하게 잠갔다).
 */
@Import({ TestcontainersConfiguration.class, SendDigestUseCaseTest.RecordingSenderConfig.class })
@SpringBootTest
@Transactional
class SendDigestUseCaseTest {

	@Autowired
	RenderDigestUseCase render;
	@Autowired
	RecordingDigestSender recordingSender;

	@Test
	void splitsIntoMultiplePartsAndSendsEachWhenAllSucceed() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		SendDigestUseCase useCase = new SendDigestUseCase(render, recordingSender, 20); // 작은 한도로 분할 강제

		DigestSendReport report = useCase.send();

		assertThat(report.parts()).isGreaterThan(1);
		assertThat(recordingSender.sent).hasSize(report.parts());
		assertThat(report.sent()).isEqualTo(report.parts());
		assertThat(report.allSucceeded()).isTrue();
	}

	@Test
	void oneFailedPartMakesTheWholeSendUnsuccessful() {
		recordingSender.results.clear();
		recordingSender.sent.clear();
		recordingSender.results.add(true);
		recordingSender.results.add(false); // 두 번째 조각만 실패
		SendDigestUseCase useCase = new SendDigestUseCase(render, recordingSender, 20);

		DigestSendReport report = useCase.send();

		assertThat(report.parts()).isGreaterThan(1);
		assertThat(report.sent()).isLessThan(report.parts());
		assertThat(report.allSucceeded()).isFalse();
	}

	static class RecordingSenderConfig {
		@Bean
		@Primary
		RecordingDigestSender recordingDigestSender() {
			return new RecordingDigestSender();
		}
	}

	/** 성공/실패를 미리 정해 순서대로 돌려주는 스파이 — 기본은 항상 성공. */
	static class RecordingDigestSender implements DigestSender {
		final List<String> sent = new ArrayList<>();
		final Queue<Boolean> results = new LinkedBlockingQueue<>();

		@Override
		public boolean sendDigest(String text) {
			sent.add(text);
			Boolean next = results.poll();
			return next == null || next;
		}
	}
}
