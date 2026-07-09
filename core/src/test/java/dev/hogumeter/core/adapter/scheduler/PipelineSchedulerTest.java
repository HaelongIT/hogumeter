package dev.hogumeter.core.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인 트리거의 순수 계약. mock 대신 람다 seam을 쓴다(이 프로젝트 테스트는 실객체를 쓴다).
 *
 * <p>스케줄러의 존재 이유는 "두 유스케이스를 주기적으로 부른다"이고, 그 계약에서 진짜 중요한 것은
 * <b>순서</b>와 <b>한 단계의 실패가 다른 단계·다음 주기를 죽이지 않는다</b>는 것이다.
 */
class PipelineSchedulerTest {

	@Test
	@DisplayName("ingest 다음 reprocess — 이번 주기에 새로 링크된 원문까지 종료 판정이 보게 한다")
	void runsIngestBeforeReprocess() {
		List<String> calls = new ArrayList<>();
		PipelineScheduler scheduler = new PipelineScheduler(() -> calls.add("ingest"), () -> calls.add("reprocess"));

		scheduler.tick();

		assertThat(calls).containsExactly("ingest", "reprocess");
	}

	@Test
	@DisplayName("ingest가 터져도 reprocess는 돌고, tick은 예외를 밖으로 내지 않는다 (스케줄러가 죽지 않는다)")
	void ingestFailureDoesNotStopReprocess() {
		List<String> calls = new ArrayList<>();
		PipelineScheduler scheduler = new PipelineScheduler(
				() -> {
					throw new IllegalStateException("DB 연결 끊김");
				},
				() -> calls.add("reprocess"));

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("reprocess");
	}

	@Test
	@DisplayName("reprocess가 터져도 tick은 정상 반환한다 — 다음 주기가 다시 시도한다")
	void reprocessFailureIsIsolated() {
		List<String> calls = new ArrayList<>();
		PipelineScheduler scheduler = new PipelineScheduler(
				() -> calls.add("ingest"),
				() -> {
					throw new IllegalStateException("낙관적 락 충돌");
				});

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("ingest");
	}

	@Test
	@DisplayName("두 단계가 다 터져도 tick은 반환한다 — 실패는 로그로 남고 주기는 유지된다")
	void bothFailuresAreIsolated() {
		PipelineScheduler scheduler = new PipelineScheduler(
				() -> {
					throw new IllegalStateException("ingest 실패");
				},
				() -> {
					throw new IllegalStateException("reprocess 실패");
				});

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
	}
}
