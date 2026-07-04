package dev.hogumeter.core.domain.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.domain.BenchmarkParams;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** BM-06 AC-7 하위 절: K_FILL = max(7, K_DISPLAY+2) 불변식(K_FILL &gt; K_DISPLAY)을 3~10 전 구간 보존. */
class BenchmarkParamsTest {

	@ParameterizedTest
	@CsvSource({ "3, 7", "4, 7", "5, 7", "6, 8", "7, 9", "8, 10", "9, 11", "10, 12" })
	void kFillIsMaxOf7AndKDisplayPlus2(int kDisplay, int expectedKFill) {
		BenchmarkParams params = BenchmarkParamsFixtures.params(kDisplay);

		assertThat(params.kFill()).isEqualTo(expectedKFill);
		assertThat(params.kFill()).isGreaterThan(kDisplay); // 불변식
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 11, 0, -1 })
	void kDisplayOutOfRangeIsRejected(int badKDisplay) {
		// V1__init.sql alert_policy.k_display CHECK (3~10)과 정렬
		assertThatThrownBy(() -> BenchmarkParamsFixtures.params(badKDisplay))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void defaultsMatchApprovedParams() {
		// docs/31 승인값(2026-07-04) 상수화 잠금 — 변경 시 decision-log 근거 필수
		BenchmarkParams d = BenchmarkParams.defaults();

		assertThat(d.mergePriceToleranceRatio()).isEqualByComparingTo(new BigDecimal("0.02"));
		assertThat(d.mergePriceToleranceFloorWon()).isEqualTo(5_000L);
		assertThat(d.mergeWindowHours()).isEqualTo(48);
		assertThat(d.outlierIqrMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));
		assertThat(d.coldStartJackpotRatio()).isEqualByComparingTo(new BigDecimal("0.30"));
		assertThat(d.kDisplay()).isEqualTo(5);
		assertThat(d.expandLimitMonths()).isEqualTo(12);
		assertThat(d.kFill()).isEqualTo(7);
	}
}
