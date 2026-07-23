package dev.hogumeter.core.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.DealRef;
import dev.hogumeter.core.domain.benchmark.Tier;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PUR-02 구매 시점 as-of 스냅샷 동결 — 순수 값(정직성: 통계 없으면 null 그대로). */
class SnapshotTest {

	private static final String BASIS = "P=6mo,K=5";

	private BenchmarkView sufficient(long benchmark) {
		return new BenchmarkView(Tier.SUFFICIENT, benchmark, 850_000L, null, null, 5, 5, null, 900_000L, null,
				List.of(), List.of());
	}

	@Test
	void sufficientViewFreezesStatsAndPaidGap() {
		Snapshot snap = Snapshot.from(sufficient(890_000L), 940_000L, BASIS);

		assertThat(snap.benchmarkPrice()).isEqualTo(890_000L);
		assertThat(snap.tier()).isEqualTo(Tier.SUFFICIENT);
		assertThat(snap.n()).isEqualTo(5);
		assertThat(snap.m()).isEqualTo(5);
		assertThat(snap.sparseLowest()).isNull();
		assertThat(snap.paidGap()).isEqualTo(50_000L); // 940k 지불 − 890k 기준 = +50k(호구 방향)
		assertThat(snap.basis()).isEqualTo(BASIS);
		assertThat(snap.unobserved()).isFalse();
	}

	@Test
	void paidBelowBenchmarkYieldsNegativeGap() {
		Snapshot snap = Snapshot.from(sufficient(890_000L), 820_000L, BASIS);

		assertThat(snap.paidGap()).isEqualTo(-70_000L); // 잘 삼
	}

	@Test
	void sparseViewHasNoBenchmarkButKeepsLowestCaseAndNullGap() {
		List<DealRef> cases = List.of(
				new DealRef(870_000L, LocalDate.of(2026, 3, 1), "ppomppu", "https://p.test/1", List.of()),
				new DealRef(820_000L, LocalDate.of(2026, 3, 2), "ruliweb", "https://r.test/2", List.of()));
		BenchmarkView sparse = new BenchmarkView(Tier.SPARSE, null, null, null, null, 2, 1, null, 900_000L, null,
				cases, List.of());

		Snapshot snap = Snapshot.from(sparse, 900_000L, BASIS);

		assertThat(snap.benchmarkPrice()).isNull();
		assertThat(snap.tier()).isEqualTo(Tier.SPARSE);
		assertThat(snap.sparseLowest()).isEqualTo(820_000L); // 사례 최저가
		assertThat(snap.paidGap()).isNull(); // 기준가 부재 → 갭 판정 불가
		assertThat(snap.unobserved()).isFalse();
	}

	@Test
	void noneViewHasNoStats() {
		BenchmarkView none = new BenchmarkView(Tier.NONE, null, null, null, null, 0, 0, null, 900_000L, null,
				List.of(), List.of());

		Snapshot snap = Snapshot.from(none, 900_000L, BASIS);

		assertThat(snap.benchmarkPrice()).isNull();
		assertThat(snap.sparseLowest()).isNull();
		assertThat(snap.paidGap()).isNull();
	}

	@Test
	void unobservedFactoryHasNoStatsButKeepsBasis() {
		Snapshot snap = Snapshot.unobserved(BASIS);

		assertThat(snap.unobserved()).isTrue();
		assertThat(snap.tier()).isNull();
		assertThat(snap.benchmarkPrice()).isNull();
		assertThat(snap.paidGap()).isNull();
		assertThat(snap.n()).isZero();
		assertThat(snap.basis()).isEqualTo(BASIS);
	}
}
