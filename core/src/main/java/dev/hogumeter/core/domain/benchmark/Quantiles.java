package dev.hogumeter.core.domain.benchmark;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 분위수 R-7(선형보간, Excel PERCENTILE.INC / R 기본). BM-06 median·P25 계약(docs/91 Q-6).
 * 정렬 오름차순 x[0..N-1], p∈[0,1]: h=(N-1)·p, Q = x[⌊h⌋] + (h−⌊h⌋)(x[⌈h⌉]−x[⌊h⌋]).
 * 원화 결과는 BigDecimal 중간계산 후 HALF_UP로 정수 원 반올림. BM-05 Tukey Q1/Q3도 이 헬퍼를 재사용.
 */
public final class Quantiles {

	public static final BigDecimal P25 = new BigDecimal("0.25");
	public static final BigDecimal P50 = new BigDecimal("0.5");

	private Quantiles() {
	}

	/** R-7 분위수(BigDecimal, 미반올림). values는 비어 있으면 안 된다. */
	public static BigDecimal percentile(List<Long> values, BigDecimal p) {
		if (values.isEmpty()) {
			throw new IllegalArgumentException("percentile of empty sample");
		}
		List<Long> sorted = new ArrayList<>(values);
		sorted.sort(null);
		int n = sorted.size();
		if (n == 1) {
			return BigDecimal.valueOf(sorted.get(0));
		}
		BigDecimal h = BigDecimal.valueOf(n - 1L).multiply(p);
		int lower = h.setScale(0, RoundingMode.FLOOR).intValueExact();
		BigDecimal frac = h.subtract(BigDecimal.valueOf(lower));
		BigDecimal lowerValue = BigDecimal.valueOf(sorted.get(lower));
		if (frac.signum() == 0) {
			return lowerValue;
		}
		BigDecimal upperValue = BigDecimal.valueOf(sorted.get(lower + 1));
		return lowerValue.add(frac.multiply(upperValue.subtract(lowerValue)));
	}

	public static BigDecimal median(List<Long> values) {
		return percentile(values, P50);
	}

	/** 정수 원으로 반올림(HALF_UP)한 분위수. */
	public static long percentileWon(List<Long> values, BigDecimal p) {
		return percentile(values, p).setScale(0, RoundingMode.HALF_UP).longValueExact();
	}

	public static long medianWon(List<Long> values) {
		return percentileWon(values, P50);
	}
}
