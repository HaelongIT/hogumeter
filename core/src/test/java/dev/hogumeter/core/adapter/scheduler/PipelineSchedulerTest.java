package dev.hogumeter.core.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.application.FlushHeldAlertsUseCase;
import dev.hogumeter.core.application.IngestReport;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파이프라인 트리거의 순수 계약. mock 대신 람다 seam을 쓴다(이 프로젝트 테스트는 실객체를 쓴다).
 *
 * <p>중요한 것 셋: <b>순서</b>(관찰만료 → ingest → 조건태그 → 가격 → 종료), <b>한 단계의 실패가 뒤 단계와 다음
 * 주기를 죽이지 않는다</b>, 그리고 <b>무슨 일이 있었는지 남긴다</b>.
 */
class PipelineSchedulerTest {

	private static final PipelineSnapshot EMPTY = new PipelineSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0);

	private final List<String> calls = new ArrayList<>();
	private final AtomicReference<PipelineTickReport> reported = new AtomicReference<>();

	private final Runnable expire = () -> calls.add("expire");
	private final Supplier<IngestReport> ingest = () -> {
		calls.add("ingest");
		return IngestReport.empty();
	};
	private final Runnable conditions = () -> calls.add("conditions");
	private final Supplier<List<Long>> prices = () -> {
		calls.add("prices");
		return List.of();
	};
	private final Supplier<List<Long>> status = () -> {
		calls.add("status");
		return List.of();
	};
	// 후속 알림은 0 반환 — 순서·격리 단언에 영향을 주지 않는다(배선 검증은 FollowUpAlertUseCase 쪽).
	private final BiFunction<List<Long>, FollowUpKind, Integer> followUp = (ids, kind) -> 0;

	private static Runnable boom(String message) {
		return () -> {
			throw new IllegalStateException(message);
		};
	}

	private static Supplier<List<Long>> boomSupplier(String message) {
		return () -> {
			throw new IllegalStateException(message);
		};
	}

	private static Supplier<IngestReport> boomIngest(String message) {
		return () -> {
			throw new IllegalStateException(message);
		};
	}

	private PipelineScheduler scheduler(Supplier<IngestReport> ingest, Supplier<List<Long>> prices,
			Supplier<List<Long>> status) {
		return new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> EMPTY, reported::set);
	}

	@Test
	@DisplayName("관찰만료 → ingest → 조건태그 → 가격 → 종료. 만료가 먼저라 끝난 관찰이 \"산 뒤 알림\"을 내지 않는다")
	void runsStepsInOrder() {
		scheduler(ingest, prices, status).tick();

		assertThat(calls).containsExactly("expire", "ingest", "conditions", "prices", "status");
	}

	@Test
	@DisplayName("ingest가 터져도 뒤 단계는 돌고, tick은 예외를 밖으로 내지 않는다 (스케줄러가 죽지 않는다)")
	void ingestFailureDoesNotStopTheRest() {
		PipelineScheduler scheduler = scheduler(boomIngest("DB 연결 끊김"), prices, status);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("expire", "conditions", "prices", "status");
	}

	@Test
	@DisplayName("가격 단계가 터져도 종료 판정은 돈다 — 단계는 서로 독립이다")
	void priceFailureDoesNotStopStatus() {
		PipelineScheduler scheduler = scheduler(ingest, boomSupplier("낙관적 락 충돌"), status);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("expire", "ingest", "conditions", "status");
	}

	@Test
	@DisplayName("종료 단계가 터져도 tick은 정상 반환한다 — 다음 주기가 다시 시도한다")
	void statusFailureIsIsolated() {
		PipelineScheduler scheduler = scheduler(ingest, prices, boomSupplier("종료 실패"));

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("expire", "ingest", "conditions", "prices");
	}

	@Test
	@DisplayName("세 단계가 다 터져도 tick은 반환하고, 그때도 보고서를 낸다 — 무엇이 남았는지가 그때 더 중요하다")
	void allFailuresStillReport() {
		PipelineScheduler scheduler = scheduler(boomIngest("a"), boomSupplier("b"), boomSupplier("c"));

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(reported.get()).isNotNull();
	}

	@Test
	@DisplayName("틱마다 전후 스냅샷을 찍어 차이를 보고한다 (OBS-02)")
	void reportsWhatTheTickDid() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(1, 0, 0, 0, 0, 1, 0, 0, 0),
				new PipelineSnapshot(1, 1, 1, 0, 0, 0, 0, 0, 0)));
		PipelineScheduler scheduler = new PipelineScheduler(expire, ingest, conditions, prices, status, followUp,
				() -> snapshots.remove(0), reported::set);

		scheduler.tick();

		assertThat(snapshots).as("전후로 정확히 두 번 찍는다").isEmpty();
		assertThat(reported.get().dealsCreated()).isEqualTo(1);
		assertThat(reported.get().pending()).isZero();
	}

	@Test
	@DisplayName("후속 알림 발송 수를 틱 리포트에 싣는다 — 첫 알림만 세고 후속을 버리면 절반 카운터다 (Q-57)")
	void followUpSendCountsFlowIntoReport() {
		// 가격변화 딜 [1,2], 종료 딜 [3,4,5]. followUp이 종류별로 다른 수를 반환한다 — 스케줄러가 그 값을
		// 붙잡아 리포트에 실어야 한다(예전엔 BiConsumer라 반환이 통째로 버려졌다).
		BiFunction<List<Long>, FollowUpKind, Integer> counting =
				(ids, kind) -> kind == FollowUpKind.PRICE_CHANGED ? 2 : 3;
		new PipelineScheduler(expire, ingest, conditions, () -> List.of(1L, 2L), () -> List.of(3L, 4L, 5L),
				counting, () -> EMPTY, reported::set).tick();

		assertThat(reported.get().followUpPriceChangedSent()).isEqualTo(2);
		assertThat(reported.get().followUpEndedSent()).isEqualTo(3);
	}

	@Test
	@DisplayName("아무 일도 없어도 보고한다 — 조용한 스케줄러는 죽은 스케줄러와 구별되지 않는다")
	void idleTickStillReports() {
		scheduler(() -> IngestReport.empty(), () -> List.of(), () -> List.of()).tick();

		assertThat(reported.get()).isNotNull();
		assertThat(reported.get().dealsCreated()).isZero();
		assertThat(reported.get().stepsFailed()).as("건강한 틱은 단계 실패 0").isZero();
	}

	@Test
	@DisplayName("방해금지 플러시 결과가 틱 리포트에 흐른다 (Q-20 ②)")
	void heldAlertFlushCountsFlowIntoReport() {
		// 플러시가 (발송 2, 드롭 1)을 내면 리포트에 그대로 실려야 한다 — 배선이 끊기면 0이라 이 테스트가 잡는다.
		new PipelineScheduler(expire, ingest, conditions, prices, status, followUp,
				() -> new FlushHeldAlertsUseCase.FlushReport(2, 1), healthy -> { }, () -> EMPTY, reported::set).tick();

		assertThat(reported.get().heldAlertsFlushed()).isEqualTo(2);
		assertThat(reported.get().heldAlertsDropped()).isEqualTo(1);
	}

	@Test
	@DisplayName("건강한 틱은 healthTick(true), 단계 실패·DB 단절은 false — 연속 실패 관리 알림의 입력 (OBS-03)")
	void feedsHealthSignalEachTick() {
		List<Boolean> health = new ArrayList<>();

		schedulerWithHealth(ingest, () -> EMPTY, health).tick(); // 정상
		schedulerWithHealth(boomIngest("스키마 불일치"), () -> EMPTY, health).tick(); // 단계 실패
		schedulerWithHealth(ingest, () -> {
			throw new IllegalStateException("db down"); // 스냅샷 실패(DB 단절)
		}, health).tick();

		assertThat(health).containsExactly(true, false, false);
	}

	private PipelineScheduler schedulerWithHealth(Supplier<IngestReport> ingest, Supplier<PipelineSnapshot> probe,
			List<Boolean> health) {
		return new PipelineScheduler(expire, ingest, conditions, prices, status, followUp,
				FlushHeldAlertsUseCase.FlushReport::empty, health::add, probe, reported::set);
	}

	@Test
	@DisplayName("단계가 터지면 stepsFailed로 센다 — 격리는 침묵이기도 하다 (Q-56)")
	void failedStepIsCountedInReport() {
		// ingest가 터진다. runStep이 격리해 뒤 단계·보고는 살지만, 그 실패가 리포트에 보여야 한다 —
		// 안 그러면 파이프라인이 '도는 척하며 아무것도 처리 안 하는' 틱이 정상 틱과 구별되지 않는다.
		new PipelineScheduler(expire, boomIngest("스키마 불일치"), conditions, prices, status, followUp,
				() -> EMPTY, reported::set).tick();

		assertThat(reported.get()).as("격리 덕에 보고는 산다").isNotNull();
		assertThat(reported.get().stepsFailed()).isEqualTo(1);
	}

	/**
	 * DB가 죽으면 스냅샷 조회부터 터진다. 그게 {@code runStep} 밖에 있으면 틱 전체가 예외로 끝나
	 * 단계는 한 번도 시도되지 않고, 그 예외를 삼키는 것은 Spring이라 우리 로그에는 아무 흔적도 없다.
	 */
	@Test
	@DisplayName("스냅샷 조회 실패(DB 단절)는 틱을 죽이지 않는다 — 단계는 그래도 시도된다")
	void probeFailureDoesNotSkipTheSteps() {
		PipelineScheduler scheduler = new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> {
			throw new IllegalStateException("connection refused");
		}, reported::set);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();

		assertThat(calls).containsExactly("expire", "ingest", "conditions", "prices", "status");
	}

	/** 스냅샷을 못 읽었으면 보고하지 않는다. 0으로 채운 리포트는 "아무 일도 없었다"는 거짓말이다. */
	@Test
	void probeFailureReportsNothingRatherThanZeroes() {
		new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> {
			throw new IllegalStateException("connection refused");
		}, reported::set).tick();

		assertThat(reported.get()).isNull();
	}

	/**
	 * 관찰 만료가 <b>ingest보다 먼저</b>인 이유: ingest는 새 딜마다 알림을 태우는데, PUR-03의
	 * "산 뒤 알림"은 {@code OBSERVING} 관찰에만 발화한다. 만료를 나중에 돌리면 <b>이미 끝난 관찰이</b>
	 * 이번 틱의 딜에 대해 한 번 더 알림을 낸다.
	 */
	@Test
	void expireRunsBeforeIngestSoEndedObservationsDoNotAlert() {
		scheduler(ingest, prices, status).tick();

		assertThat(calls.indexOf("expire")).isLessThan(calls.indexOf("ingest"));
	}

	@Test
	@DisplayName("관찰 만료가 터져도 파이프라인은 돈다 — 딜 수집이 구매 기록에 인질로 잡히지 않는다")
	void expireFailureDoesNotStopThePipeline() {
		PipelineScheduler scheduler = new PipelineScheduler(boom("만료 실패"), ingest, conditions, prices, status, followUp,
				() -> EMPTY, reported::set);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("ingest", "conditions", "prices", "status");
	}

	/** OBS-02: 만료 건수도 센다. REPORT_PENDING의 증가분이라 동시에 들어온 새 구매에 오염되지 않는다. */
	@Test
	void reportsHowManyObservationsExpired() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 1, 0, 0),
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 3, 0, 0)));
		new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> snapshots.remove(0), reported::set).tick();

		assertThat(reported.get().purchasesExpired()).isEqualTo(2);
	}

	/**
	 * 조건 태그 보존이 <b>ingest 바로 뒤</b>인 이유: ingest가 방금 만든 링크({@code deal_event_source})가
	 * 있어야 원문의 태그를 딜로 끌어올릴 수 있다. 앞에 두면 새 딜은 다음 틱까지 무조건 가격 행세를 한다.
	 */
	@Test
	void conditionsRunRightAfterIngestSoNewDealsAreTaggedInTheSameTick() {
		scheduler(ingest, prices, status).tick();

		assertThat(calls.indexOf("conditions")).isEqualTo(calls.indexOf("ingest") + 1);
		assertThat(calls.indexOf("conditions")).isLessThan(calls.indexOf("prices"));
	}

	@Test
	@DisplayName("조건 태그 단계가 터져도 가격·종료는 돈다 — 태그는 표시용이고 파이프라인의 인질이 아니다")
	void conditionsFailureDoesNotStopThePipeline() {
		PipelineScheduler scheduler = new PipelineScheduler(expire, ingest, boom("배열 변환 실패"), prices, status, followUp,
				() -> EMPTY, reported::set);

		assertThatCode(scheduler::tick).doesNotThrowAnyException();
		assertThat(calls).containsExactly("expire", "ingest", "prices", "status");
	}

	/**
	 * OBS-02: 배송비 미상 딜은 조건부 딜의 <b>진부분집합</b>이고 성질이 다르다 — 조건부 가격은 as-posted로
	 * 옳지만, 배송비를 모른 채 0을 더한 값은 기준가를 실제보다 아래로 끈다. 합쳐 세면 아무것도 말하지 않는다.
	 */
	@Test
	@DisplayName("배송비 미상 딜을 따로 센다 — 이 수가 표본 오염률이고, 지금 그걸 보는 유일한 창이다")
	void reportsShippingUnknownSeparatelyFromConditional() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 0, 5, 1),
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 0, 9, 4)));
		new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> snapshots.remove(0), reported::set)
				.tick();

		assertThat(reported.get().conditionalTotal()).isEqualTo(9);
		assertThat(reported.get().shippingUnknownTotal()).isEqualTo(4);
		assertThat(reported.get().toString()).contains("shippingUnknownTotal=4");
	}

	/**
	 * OBS-02: 조건부 딜은 <b>차이와 절대 수를 함께</b> 낸다. 태그가 붙은 딜은 기준가 표본 안에 그대로
	 * 있으므로(as-posted), 그 절대 수가 이 사실을 볼 수 있는 유일한 창이다(화면 표시는 미구현, Q-46).
	 */
	@Test
	void reportsConditionalDealsTaggedAndTheRunningTotal() {
		List<PipelineSnapshot> snapshots = new ArrayList<>(List.of(
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 0, 2, 0),
				new PipelineSnapshot(0, 0, 0, 0, 0, 0, 0, 5, 0)));
		new PipelineScheduler(expire, ingest, conditions, prices, status, followUp, () -> snapshots.remove(0), reported::set)
				.tick();

		assertThat(reported.get().conditionsTagged()).isEqualTo(3);
		assertThat(reported.get().conditionalTotal()).isEqualTo(5);
	}

	/**
	 * Q-67 배선: 재처리가 낸 전이 id가 후속 알림으로 <b>종류를 지켜</b> 흐른다. 부품별 GREEN은 이 경로를
	 * 보장하지 않는다 — 가격변화 id는 PRICE_CHANGED로, 종료 id는 ENDED로 가야 한다(뒤바뀌면 "끝났다"를
	 * 가격변화로 알린다). 이 좁은 테스트가 그 관을 관통한다.
	 */
	@Test
	@DisplayName("가격변화 딜 id는 PRICE_CHANGED로, 종료 딜 id는 ENDED로 후속 알림에 흘러간다")
	void routesReprocessedIdsToFollowUpByKind() {
		Map<FollowUpKind, List<Long>> received = new EnumMap<>(FollowUpKind.class);
		Supplier<List<Long>> pricesReturning = () -> List.of(11L, 22L);
		Supplier<List<Long>> statusReturning = () -> List.of(33L);
		BiFunction<List<Long>, FollowUpKind, Integer> capture = (ids, kind) -> {
			received.put(kind, ids);
			return ids.size();
		};

		new PipelineScheduler(expire, ingest, conditions, pricesReturning, statusReturning, capture,
				() -> EMPTY, reported::set).tick();

		assertThat(received.get(FollowUpKind.PRICE_CHANGED)).containsExactly(11L, 22L);
		assertThat(received.get(FollowUpKind.ENDED)).containsExactly(33L);
	}
}
