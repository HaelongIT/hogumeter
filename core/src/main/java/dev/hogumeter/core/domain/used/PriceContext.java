package dev.hogumeter.core.domain.used;

import java.util.List;

/**
 * USED-04 AC-13 ① 가격 맥락. <b>기준가를 합성하지 않는다</b>(docs/used/00) — median·P25 같은 중고
 * 통계는 여기 없다. 신품 기준가 대비 %와 활성 매물 스냅샷을 <b>가공 없이 나열</b>할 뿐이다.
 *
 * @param benchmarkComparisonPercent 신품 기준가 대비 %(양수=더 쌈). 기준가가 없으면 null — 지어내지 않는다
 * @param activeSnapshotPrices 같은 조건검색의 활성 매물 가격 나열(통계 가공 없음)
 * @param source 스냅샷 출처 표기(예: "번개장터 활성 매물") — 직거래·택배 맥락 차이를 사람이 인지하도록
 */
public record PriceContext(Integer benchmarkComparisonPercent, List<Long> activeSnapshotPrices, String source) {

	public PriceContext {
		activeSnapshotPrices = List.copyOf(activeSnapshotPrices);
	}
}
