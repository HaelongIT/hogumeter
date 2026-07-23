package dev.hogumeter.core.domain.used;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * USED-04 AC-13 ① 가격 맥락(순수) — <b>기준가를 합성하지 않는다</b>: 여기서 만드는 건 신품 기준가 대비
 * %와 활성 매물 스냅샷의 <b>나열</b>뿐이다. median·P25 같은 통계는 절대 만들지 않는다(docs/used/00).
 */
class PriceContextCalculatorTest {

	@Test
	@DisplayName("기준가보다 싸면 양수 % — '기준가 대비 20% 쌈'")
	void cheaperThanBenchmarkIsPositivePercent() {
		PriceContext ctx = PriceContextCalculator.compute(800_000L, List.of(750_000L, 900_000L), 1_000_000L, "번개장터 활성 매물");

		assertThat(ctx.benchmarkComparisonPercent()).isEqualTo(20);
		assertThat(ctx.activeSnapshotPrices()).containsExactly(750_000L, 900_000L); // 가공 없이 그대로
		assertThat(ctx.source()).isEqualTo("번개장터 활성 매물");
	}

	@Test
	@DisplayName("기준가보다 비싸면 음수 %")
	void pricierThanBenchmarkIsNegativePercent() {
		PriceContext ctx = PriceContextCalculator.compute(1_200_000L, List.of(), 1_000_000L, "번개장터 활성 매물");

		assertThat(ctx.benchmarkComparisonPercent()).isEqualTo(-20);
	}

	@Test
	@DisplayName("기준가가 없으면(SPARSE/NONE 등) % 대신 null — 지어내지 않는다")
	void noBenchmarkYieldsNullPercent() {
		PriceContext ctx = PriceContextCalculator.compute(800_000L, List.of(750_000L), null, "번개장터 활성 매물");

		assertThat(ctx.benchmarkComparisonPercent()).isNull();
		assertThat(ctx.activeSnapshotPrices()).containsExactly(750_000L); // 스냅샷은 기준가 유무와 무관히 나온다
	}

	@Test
	@DisplayName("기준가 0은 실제 값이 아니다 — 나눗셈을 피하고 null로 다룬다 (Q-53 교훈)")
	void zeroBenchmarkIsTreatedAsAbsent() {
		PriceContext ctx = PriceContextCalculator.compute(800_000L, List.of(), 0L, "번개장터 활성 매물");

		assertThat(ctx.benchmarkComparisonPercent()).isNull();
	}

	@Test
	@DisplayName("빈 스냅샷도 빈 목록으로 그대로 나온다 — null이 아니다")
	void emptySnapshotStaysEmpty() {
		PriceContext ctx = PriceContextCalculator.compute(800_000L, List.of(), 1_000_000L, "번개장터 활성 매물");

		assertThat(ctx.activeSnapshotPrices()).isEmpty();
	}
}
