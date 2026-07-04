package dev.hogumeter.core.domain.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.Quantiles;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 분위수 R-7(선형보간, Excel PERCENTILE.INC/R 기본) 정확값 고정 — BM-06 median/P25 계약(docs/91 Q-6).
 * 기대값은 손계산으로 도출: h=(N-1)·p, Q = x[⌊h⌋] + (h−⌊h⌋)(x[⌈h⌉]−x[⌊h⌋]).
 */
class QuantilesTest {

	@Test
	void medianOfOddCountIsMiddleValue() {
		// N=3, p0.5: h=1.0 → x[1]
		assertThat(Quantiles.medianWon(List.of(800_000L, 850_000L, 900_000L))).isEqualTo(850_000L);
	}

	@Test
	void medianOfEvenCountInterpolatesMiddleTwo() {
		// N=2, p0.5: h=0.5 → 800000 + 0.5·100000
		assertThat(Quantiles.medianWon(List.of(800_000L, 900_000L))).isEqualTo(850_000L);
	}

	@Test
	void medianIsOrderIndependent() {
		assertThat(Quantiles.medianWon(List.of(900_000L, 800_000L, 850_000L))).isEqualTo(850_000L);
	}

	@Test
	void p25OfTwoValuesLinearInterpolation() {
		// N=2, p0.25: h=0.25 → 820000 + 0.25·80000
		assertThat(Quantiles.percentileWon(List.of(820_000L, 900_000L), Quantiles.P25)).isEqualTo(840_000L);
	}

	@Test
	void p25OfThreeValues() {
		// N=3, p0.25: h=0.5 → 800000 + 0.5·50000
		assertThat(Quantiles.percentileWon(List.of(800_000L, 850_000L, 900_000L), Quantiles.P25)).isEqualTo(825_000L);
	}

	@Test
	void singleValueYieldsThatValueForAnyPercentile() {
		assertThat(Quantiles.medianWon(List.of(890_000L))).isEqualTo(890_000L);
		assertThat(Quantiles.percentileWon(List.of(890_000L), Quantiles.P25)).isEqualTo(890_000L);
	}

	@Test
	void interpolatedResultRoundsHalfUpToWon() {
		// N=2, p0.5: 800000 + 0.5·(800001-800000) = 800000.5 → HALF_UP → 800001
		assertThat(Quantiles.medianWon(List.of(800_000L, 800_001L))).isEqualTo(800_001L);
	}
}
