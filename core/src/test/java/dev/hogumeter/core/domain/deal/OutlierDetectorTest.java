package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.BenchmarkParams;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * BM-05 양방향 이상치 판정(Tukey IQR). 분포 {800..860}k → Q1=815k, Q3=845k, IQR=30k,
 * 컷 = [815k−1.5·30k, 845k+1.5·30k] = [770k, 890k]. 컷 경계는 이상치 아님(포함).
 */
class OutlierDetectorTest {

	private static final List<Long> DISTRIBUTION = List.of(
			800_000L, 810_000L, 820_000L, 830_000L, 840_000L, 850_000L, 860_000L);

	private final OutlierDetector detector = new OutlierDetector();
	private final BenchmarkParams params = BenchmarkParams.defaults(); // IQR 배수 1.5

	// ---- AC-1 UPPER / AC-2 LOWER / 정상 NONE (컷 경계 포함) ----
	@ParameterizedTest(name = "price={0} → {1}")
	@CsvSource({
			"5000000, UPPER", "890001, UPPER", "890000, NONE", "830000, NONE",
			"770000, NONE", "769999, LOWER", "700000, LOWER"
	})
	void classifiesByTukeyIqrCut(long price, OutlierFlag expected) {
		assertThat(detector.classify(price, DISTRIBUTION, params)).isEqualTo(expected);
	}

	// ---- AC-5 SPARSE 폴백: 현재가 대비 비상식(±50% 잠정) → 잠정 제외 대상 ----
	@ParameterizedTest(name = "price={0}, current=1,000,000 → absurd={1}")
	@CsvSource({
			"300000, true", "499999, true", "500000, false", "900000, false",
			"1500000, false", "1500001, true", "2000000, true"
	})
	void sparseFallbackFlagsAbsurdPricesVsCurrent(long price, boolean expected) {
		assertThat(detector.isAbsurdVsCurrent(price, 1_000_000L, new BigDecimal("0.5"))).isEqualTo(expected);
	}
}
