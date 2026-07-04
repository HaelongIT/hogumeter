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
 * @param cases SPARSE일 때 사례 나열(가격·날짜·출처), 그 외 빈 리스트
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
		long currentPrice,
		Gap gap,
		List<DealRef> cases) {

	public record PricePoint(long price, LocalDate date) {
	}

	public record DealRef(long price, LocalDate date, String site, String sourceUrl) {
	}

	/** 현재가 대비 갭(원·% 병기). 참조가(기준가/최저가) 부재 시 해당 leg는 null. */
	public record Gap(Leg vsBenchmark, Leg vsLowest) {
		public record Leg(long won, BigDecimal pct) {
		}
	}
}
