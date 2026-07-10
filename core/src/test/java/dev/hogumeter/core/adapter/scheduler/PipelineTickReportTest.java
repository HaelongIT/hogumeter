package dev.hogumeter.core.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** OBS-02 카운터의 순수 산술. 로그 문구가 아니라 값을 시험한다. */
class PipelineTickReportTest {

	private static PipelineSnapshot snapshot(long raw, long sources, long deals, long queue, long ended,
			long unprocessed) {
		return snapshot(raw, sources, deals, queue, ended, unprocessed, 0);
	}

	private static PipelineSnapshot snapshot(long raw, long sources, long deals, long queue, long ended,
			long unprocessed, long reportPending) {
		return new PipelineSnapshot(raw, sources, deals, queue, ended, unprocessed, reportPending, 0);
	}

	@Test
	@DisplayName("새 딜 하나 — 원문 1건이 링크되고 딜 1건이 생긴다(병합 0)")
	void newDeal() {
		PipelineTickReport report = PipelineTickReport.between(
				snapshot(1, 0, 0, 0, 0, 1),
				snapshot(1, 1, 1, 0, 0, 0));

		assertThat(report.postsLinked()).isEqualTo(1);
		assertThat(report.dealsCreated()).isEqualTo(1);
		assertThat(report.merged()).isZero();
		assertThat(report.pending()).isZero();
	}

	@Test
	@DisplayName("병합 — 링크는 늘었는데 딜은 안 늘었다. 그 차이가 흡수된 원문 수다(BM-04)")
	void mergedDeal() {
		PipelineTickReport report = PipelineTickReport.between(
				snapshot(2, 1, 1, 0, 0, 1),
				snapshot(2, 2, 1, 0, 0, 0));

		assertThat(report.postsLinked()).isEqualTo(1);
		assertThat(report.dealsCreated()).isZero();
		assertThat(report.merged()).isEqualTo(1);
	}

	@Test
	@DisplayName("매칭 실패는 큐로 간다 — 원문은 링크되지 않고 pending에 남는다")
	void unmatchedGoesToQueue() {
		PipelineTickReport report = PipelineTickReport.between(
				snapshot(1, 0, 0, 0, 0, 1),
				snapshot(1, 0, 0, 1, 0, 1));

		assertThat(report.queued()).isEqualTo(1);
		assertThat(report.postsLinked()).isZero();
		assertThat(report.pending())
				.as("링크되지 않은 원문은 다음 틱에도 다시 스캔된다(docs/91 Q-27 ④)")
				.isEqualTo(1);
	}

	@Test
	@DisplayName("상태 재처리 — 종료된 딜이 늘어난다")
	void endedDeals() {
		PipelineTickReport report = PipelineTickReport.between(
				snapshot(1, 1, 1, 0, 0, 0),
				snapshot(1, 1, 1, 0, 1, 0));

		assertThat(report.ended()).isEqualTo(1);
	}

	/**
	 * PUR-01 관찰 만료. REPORT_PENDING의 증가분으로 센다 — OBSERVING 차이로 세면 틱 도중 REST로 들어온
	 * 새 구매가 카운터를 오염시킨다(REPORT_PENDING은 스케줄러만 늘린다).
	 */
	@Test
	@DisplayName("관찰 만료 — 성적 집계 대기가 늘어난 만큼이 이번 틱에 만료된 관찰이다")
	void expiredObservations() {
		PipelineTickReport report = PipelineTickReport.between(
				snapshot(0, 0, 0, 0, 0, 0, 1),
				snapshot(0, 0, 0, 0, 0, 0, 3));

		assertThat(report.purchasesExpired()).isEqualTo(2);
	}

	@Test
	@DisplayName("아무 일도 없었던 틱도 0으로 보고한다 — 0을 생략하면 \"성공했는데 0건\"이 사라진다")
	void idleTickReportsZeros() {
		PipelineSnapshot same = snapshot(5, 5, 3, 1, 1, 0, 2);

		PipelineTickReport report = PipelineTickReport.between(same, same);

		assertThat(report.postsLinked()).isZero();
		assertThat(report.dealsCreated()).isZero();
		assertThat(report.merged()).isZero();
		assertThat(report.queued()).isZero();
		assertThat(report.ended()).isZero();
		assertThat(report.purchasesExpired()).isZero();
		assertThat(report.pending()).isZero();
		assertThat(report.rawTotal()).isEqualTo(5);
	}

	@Test
	@DisplayName("한 줄 요약은 ASCII만 — 콘솔 인코딩이 로그를 죽이지 않는다")
	void summaryIsAsciiOnly() {
		String summary = PipelineTickReport.between(snapshot(1, 0, 0, 0, 0, 1), snapshot(1, 1, 1, 0, 0, 0))
				.toString();

		assertThat(summary).matches("\\p{ASCII}+");
		assertThat(summary).contains("dealsCreated=1", "pending=0");
	}
}
