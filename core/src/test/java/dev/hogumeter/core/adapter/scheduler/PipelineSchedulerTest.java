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
 * <p>중요한 것 셋: <b>순서</b>(ingest → 가격 → 종료), <b>한 단계의 실패가 뒤 단계와 다음 주기를
 * 죽이지 않는다</b>, 그리고 <b>무슨 일이 있었는지 남긴다</b>.
 */
class PipelineSchedulerTest {

	private static final PipelineSnapshot EMPTY = new PipelineSnapshot(0, 0, 0, 0, 0, 0);

	private final List<String> calls = new ArrayList<>();
	private final AtomicReference<PipelineTickReport> reported = new AtomicReference<>();

	private final Runnable ingest = () -> calls.add("ingest");
	private final Runnable prices = () -> calls.add("prices");
	private final Runnable status = () -> calls.add("status");

	private static Runnable boom(String message) {
		return () -> {
			throw new IllegalStateException(message);
		};
	}

	private PipelineScheduler scheduler(Runnable ingest, Runnable prices, Runnable status) {
		return new PipelineScheduler(ingest, prices, status, () -> EMPTY, reported::set);
	}

	@Test
	@DisplayName("ingest → 가격 → 종료. 종료가 마지막이라 닫히기 직전의 가격까지 반영된다")
	void runsStepsInOrder() {
		scheduler(ingest, prices, status).tick();

		assertThat(calls).containsExactly("ingest", "prices", "status");
	}

	@Test
	@DisplayName("ingest가 터져도 뒤 단계는 돌고, tick은 예외를 밖으로 내지 않는다 (스케줄러가 죽지 않는다)")
	void ingestFailureDoesNotStopTheRest() {
		PipelineScheduler scheduler = scheduler(boom("DB 연결 끊김"), prices, status);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("prices", "status");
	}

	@Test
	@DisplayName("가격 단계가 터져도 종료 판정은 돈다 — 단계는 서로 독립이다")
	void priceFailureDoesNotStopStatus() {
		PipelineScheduler scheduler = scheduler(ingest, boom("낙관적 락 충돌"), status);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("ingest", "status");
	}

	@Test
	@DisplayName("종료 단계가 터져도 tick은 정상 반환한다 — 다음 주기가 다시 시도한다")
	void statusFailureIsIsolated() {
		PipelineScheduler scheduler = scheduler(ingest, prices, boom("종료 실패"));

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("ingest", "prices");
	}

	@Test
	@DisplayName("세 단계가 다 터져도 tick은 반환하고, 그때도 보고서를 낸다 — 무엇이 남았는지가 그때 더 중요하다")
	void allFailuresStillReport() {
		PipelineScheduler scheduler = scheduler(boom("a"), boom("b"), boom("c"));

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(reported.get()).isNotNull();
	}

	@Test
	@DisplayName("틱마다 전후 스냅샷을 찍어 차이를 보고한다 (OBS-02)")
	void reportsWhatTheTickDid() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(1, 0, 0, 0, 0, 1),
				new PipelineSnapshot(1, 1, 1, 0, 0, 0)));
		PipelineScheduler scheduler = new PipelineScheduler(ingest, prices, status,
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
		}, () -> {
		}).tick();

		assertThat(reported.get()).isNotNull();
		assertThat(reported.get().dealsCreated()).isZero();
	}

	/**
	 * DB가 죽으면 스냅샷 조회부터 터진다. 그게 {@code runStep} 밖에 있으면 틱 전체가 예외로 끝나
	 * 단계는 한 번도 시도되지 않고, 그 예외를 삼키는 것은 Spring이라 우리 로그에는 아무 흔적도 없다.
	 */
	@Test
	@DisplayName("스냅샷 조회 실패(DB 단절)는 틱을 죽이지 않는다 — 단계는 그래도 시도된다")
	void probeFailureDoesNotSkipTheSteps() {
		PipelineScheduler scheduler = new PipelineScheduler(ingest, prices, status, () -> {
			throw new IllegalStateException("connection refused");
		}, reported::set);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();

		assertThat(calls).containsExactly("ingest", "prices", "status");
	}

	/** 스냅샷을 못 읽었으면 보고하지 않는다. 0으로 채운 리포트는 "아무 일도 없었다"는 거짓말이다. */
	@Test
	void probeFailureReportsNothingRatherThanZeroes() {
		new PipelineScheduler(ingest, prices, status, () -> {
			throw new IllegalStateException("connection refused");
		}, reported::set).tick();

		assertThat(reported.get()).isNull();
	}
}
