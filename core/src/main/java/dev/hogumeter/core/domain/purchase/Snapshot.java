package dev.hogumeter.core.domain.purchase;

import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;

/**
 * PUR-02 구매 시점 as-of 스냅샷(순수 값). purchasedAt 시점 기준가 + 기록 시점 설정(basis)을 동결한다 —
 * 저장 후 불변인 "그때의 판단 근거"로, 이후 유입·설정 변경에 영향받지 않는다.
 *
 * <p>정직성(docs/03 상위 원칙): SPARSE/NONE·UNOBSERVED에서 benchmarkPrice·paidGap은 null이다.
 * 표시 계층 재량이 아니라 도메인 계약 — 통계 없음을 그대로 노출한다.
 *
 * @param benchmarkPrice as-of 기준가(SUFFICIENT만), 그 외 null
 * @param tier 표본 등급(as-of), UNOBSERVED이면 null
 * @param sparseLowest SPARSE 사례 최저가, 그 외 null
 * @param paidGap paidPrice − benchmarkPrice(호구 방향 +), 기준가 부재 시 null
 * @param basis 동결된 산출 설정 서명(예: "P=6mo,K=5")
 * @param unobserved purchasedAt < observedFrom(관측 시작 이전 구매)
 */
public record Snapshot(
		Long benchmarkPrice,
		Tier tier,
		int n,
		int m,
		Long sparseLowest,
		Long paidGap,
		String basis,
		boolean unobserved) {

	/** as-of 기준가 뷰에서 스냅샷 동결. */
	public static Snapshot from(BenchmarkView view, long paidPrice, String basis) {
		Long sparseLowest = null;
		if (view.tier() == Tier.SPARSE && !view.cases().isEmpty()) {
			sparseLowest = view.cases().stream().mapToLong(BenchmarkView.DealRef::price).min().getAsLong();
		}
		Long paidGap = view.benchmarkPrice() != null ? paidPrice - view.benchmarkPrice() : null;
		return new Snapshot(view.benchmarkPrice(), view.tier(), view.n(), view.m(),
				sparseLowest, paidGap, basis, false);
	}

	/** 관측 시작 이전 구매 — 통계 없음(UNOBSERVED). */
	public static Snapshot unobserved(String basis) {
		return new Snapshot(null, null, 0, 0, null, null, basis, true);
	}
}
