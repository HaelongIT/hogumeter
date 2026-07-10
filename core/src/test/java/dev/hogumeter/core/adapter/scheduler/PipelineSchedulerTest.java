package dev.hogumeter.core.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인 트리거의 순수 계약. mock 대신 람다 seam을 쓴다(이 프로젝트 테스트는 실객체를 쓴다).
 *
 * <p>스케줄러의 존재 이유는 "두 유스케이스를 주기적으로 부른다"이고, 그 계약에서 진짜 중요한 것은
 * <b>순서</b>와 <b>한 단계의 실패가 다른 단계·다음 주기를 죽이지 않는다</b>는 것, 그리고
 * <b>무슨 일이 있었는지 남긴다</b>는 것이다.
 */
class PipelineSchedulerTest {

	private static final PipelineSnapshot EMPTY = new PipelineSnapshot(0, 0, 0, 0, 0, 0);

	private final List<String> calls = new ArrayList<>();
	private final AtomicReference<PipelineTickReport> reported = new AtomicReference<>();

	private PipelineScheduler scheduler(Runnable ingest, Runnable reprocess) {
		return new PipelineScheduler(ingest, reprocess, () -> EMPTY, reported::set);
	}

	@Test
	@DisplayName("ingest 다음 reprocess — 이번 주기에 새로 링크된 원문까지 종료 판정이 보게 한다")
	void runsIngestBeforeReprocess() {
		scheduler(() -> calls.add("ingest"), () -> calls.add("reprocess")).tick();

		assertThat(calls).containsExactly("ingest", "reprocess");
	}

	@Test
	@DisplayName("ingest가 터져도 reprocess는 돌고, tick은 예외를 밖으로 내지 않는다 (스케줄러가 죽지 않는다)")
	void ingestFailureDoesNotStopReprocess() {
		PipelineScheduler scheduler = scheduler(
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
		PipelineScheduler scheduler = scheduler(
				() -> calls.add("ingest"),
				() -> {
					throw new IllegalStateException("낙관적 락 충돌");
				});

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("ingest");
	}

	@Test
	@DisplayName("두 단계가 다 터져도 tick은 반환하고, 그때도 보고서를 낸다 — 무엇이 남았는지가 그때 더 중요하다")
	void bothFailuresStillReport() {
		PipelineScheduler scheduler = scheduler(
				() -> {
					throw new IllegalStateException("ingest 실패");
				},
				() -> {
					throw new IllegalStateException("reprocess 실패");
				});

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(reported.get()).isNotNull();
	}

	@Test
	@DisplayName("틱마다 전후 스냅샷을 찍어 차이를 보고한다 (OBS-02)")
	void reportsWhatTheTickDid() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(1, 0, 0, 0, 0, 1),
				new PipelineSnapshot(1, 1, 1, 0, 0, 0)));
		PipelineScheduler scheduler = new PipelineScheduler(
				() -> calls.add("ingest"), () -> calls.add("reprocess"),
				() -> snapshots.remove(0), reported::set);

		scheduler.tick();

		assertThat(snapshots).as("전후로 정확히 두 번 찍는다").isEmpty();
		assertThat(reported.get().dealsCreated()).isEqualTo(1);
		assertThat(reported.get().pending()).isZero();
	}

	@Test
	@DisplayName("아무 일도 없어도 보고한다 — 조용한 스케줄러는 죽은 스케줄러와 구별되지 않는다")
	void idleTickStillReports() {
		scheduler(() -> {
		}, () -> {
		}).tick();

		assertThat(reported.get()).isNotNull();
		assertThat(reported.get().dealsCreated()).isZero();
	}
}
