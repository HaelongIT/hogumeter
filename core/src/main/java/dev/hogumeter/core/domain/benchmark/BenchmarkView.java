package dev.hogumeter.core.domain.benchmark;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 기준가 조회 결과 계약(BM-06, docs/benchmark/03 line 12~26). 저장하지 않고 매 조회 시 재계산.
 * 정직성 강제: SPARSE/NONE에서 통계 필드(benchmarkPrice·goodDealLine·periodLowest)는 null —
 * 표시 계층 재량이 아니라 도메인 계약이다.
 *
 * @param n 전체 유효 딜(교차+단일+백필, 이상치 제외) — 판정 단위
 * @param m 교차검증 딜 수 — 신뢰 표시 전용("n건(교차 m건)")
 * @param expandedToMonths 자동확장 발동 시 실효 개월, 아니면 null
 * @param currentPrice 현재가 — <b>미확립이면 null</b>(네이버 키 미발급, Q-53). 0을 sentinel로 쓰지
 *     않는다 — 0이면 갭이 −100%가 되어 "지금 100% 싸다"는 거짓말이 된다. null이면 gap의 두 leg도 null.
 * @param cases SPARSE일 때 사례 나열(가격·날짜·출처), 그 외 빈 리스트
 * @param outliers 표시 손잡이(Q-11) — {@code includeOutliers=true}일 때만 채워지는 <b>표시 전용</b>
 *     목록. 계산 진실(n·tier·benchmarkPrice)에는 손잡이와 무관하게 항상 영향을 주지 않는다 —
 *     이상치는 여기서만 보이고, 위 통계에서는 이 손잡이가 꺼져 있어도 켜져 있어도 항상 제외된다.
 */
public record BenchmarkView(
		Tier tier,
		Long benchmarkPrice,
		Long goodDealLine,
		PricePoint periodLowest,
		DealRef latestDeal,
		int n,
		int m,
		Integer expandedToMonths,
		Long currentPrice,
		Gap gap,
		List<DealRef> cases,
		List<DealRef> outliers) {

	public record PricePoint(long price, LocalDate date) {
	}

	/**
	 * 사례·최근딜 한 건. {@code conditions}는 BM-02 조건 태그(`카할` 등) — 사람이 "정상 가격"으로
	 * 오인하지 않게 사례에 병기한다(Q-46 ①). 배송비미상은 표본에서 이미 빠지므로(②) 여기 나오지 않는다.
	 */
	public record DealRef(long price, LocalDate date, String site, String sourceUrl, List<String> conditions) {
		public DealRef {
			conditions = List.copyOf(conditions);
		}
	}

	/** 현재가 대비 갭(원·% 병기). 참조가(기준가/최저가) 부재 시 해당 leg는 null. */
	public record Gap(Leg vsBenchmark, Leg vsLowest) {
		public record Leg(long won, BigDecimal pct) {
		}
	}
}
