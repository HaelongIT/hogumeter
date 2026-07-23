package dev.hogumeter.core.domain.used;

import java.util.List;

/**
 * USED-04 AC-13 ① 가격 맥락 계산(순수, IO 0). 통계를 만들지 않는다 — % 하나와 나열뿐이다.
 */
public final class PriceContextCalculator {

	private PriceContextCalculator() {
	}

	/**
	 * @param price 평가 대상 매물 가격
	 * @param activeSnapshotPrices 활성 매물 스냅샷(가공 없이 그대로 실린다)
	 * @param benchmarkPrice 신품 기준가. null 또는 0이면 "기준가 없음"으로 다뤄 %를 내지 않는다
	 *     (0을 실제 값으로 나누면 Q-53과 같은 함정 — "0원 대비 100% 쌈"이 나온다)
	 * @param source 스냅샷 출처 표기
	 */
	public static PriceContext compute(long price, List<Long> activeSnapshotPrices, Long benchmarkPrice,
			String source) {
		Integer percent = (benchmarkPrice == null || benchmarkPrice <= 0)
				? null
				: (int) Math.round((benchmarkPrice - price) * 100.0 / benchmarkPrice);
		return new PriceContext(percent, activeSnapshotPrices, source);
	}
}
