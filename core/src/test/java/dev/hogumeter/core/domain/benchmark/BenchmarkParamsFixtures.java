package dev.hogumeter.core.domain.benchmark;

import dev.hogumeter.core.domain.BenchmarkParams;
import java.math.BigDecimal;

/**
 * BenchmarkParams 테스트 전용 팩토리 — 수치는 테스트에서 명시 주입(docs/benchmark/06 line 15).
 * docs/31 승인값을 기본으로 하되 kDisplay는 경계 테스트에서 가변.
 */
final class BenchmarkParamsFixtures {

	private BenchmarkParamsFixtures() {
	}

	static BenchmarkParams params(int kDisplay) {
		return new BenchmarkParams(
				new BigDecimal("0.02"),   // mergePriceToleranceRatio ±2%
				5_000L,                    // mergePriceToleranceFloorWon
				48,                        // mergeWindowHours
				new BigDecimal("1.5"),     // outlierIqrMultiplier
				new BigDecimal("0.30"),    // coldStartJackpotRatio
				kDisplay,
				12);                       // expandLimitMonths
	}

	static BenchmarkParams defaultParams() {
		return params(5);
	}
}
