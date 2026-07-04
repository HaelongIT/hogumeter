package dev.hogumeter.core.domain.deal;

import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.Quantiles;
import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BM-05 양방향 이상치 판정(순수 도메인). Tukey IQR: Q1−k·IQR 미만=LOWER, Q3+k·IQR 초과=UPPER, 그 외 NONE.
 * 컷 경계는 이상치 아님(포함). SPARSE 구간은 IQR 불안정 → 현재가 대비 폴백 컷(AC-5).
 * 🔥 최우선 알림·큐 영속화는 AL/어댑터 관심사 — 여기선 플래그·리뷰 항목 값만 만든다.
 */
public class OutlierDetector {

	/** 분포에 대한 Tukey IQR 컷으로 price를 UPPER/LOWER/NONE 판정(AC-1·AC-2). */
	public OutlierFlag classify(long price, List<Long> distribution, BenchmarkParams params) {
		BigDecimal q1 = Quantiles.percentile(distribution, Quantiles.P25);
		BigDecimal q3 = Quantiles.percentile(distribution, Quantiles.P75);
		BigDecimal margin = q3.subtract(q1).multiply(params.outlierIqrMultiplier());
		BigDecimal lowerCut = q1.subtract(margin);
		BigDecimal upperCut = q3.add(margin);
		BigDecimal p = BigDecimal.valueOf(price);
		if (p.compareTo(lowerCut) < 0) {
			return OutlierFlag.LOWER;
		}
		if (p.compareTo(upperCut) > 0) {
			return OutlierFlag.UPPER;
		}
		return OutlierFlag.NONE;
	}

	/** LOWER 이상치 → OUTLIER_LOWER 리뷰 항목(AC-2). UPPER·NONE은 항목 없음. */
	public Optional<ReviewQueueItem> reviewItemFor(DealEvent deal) {
		if (deal.outlierFlag() != OutlierFlag.LOWER) {
			return Optional.empty();
		}
		return Optional.of(new ReviewQueueItem(ReviewQueueType.OUTLIER_LOWER, Map.of(
				"priceFirst", deal.priceFirst(),
				"site", deal.site(),
				"sourceUrl", deal.sourceUrl())));
	}

	/**
	 * AC-5 SPARSE 폴백: 현재가 대비 ±absurdityRatio 밴드를 벗어난 "비상식 가격"이면 잠정 제외 대상.
	 * absurdityRatio는 아직 미승인 잠정 파라미터(docs/91 Q-14) — 주입값 사용, 승인 후 BenchmarkParams로 이관.
	 */
	public boolean isAbsurdVsCurrent(long price, long currentPrice, BigDecimal absurdityRatio) {
		BigDecimal current = BigDecimal.valueOf(currentPrice);
		BigDecimal lowerBand = current.multiply(BigDecimal.ONE.subtract(absurdityRatio));
		BigDecimal upperBand = current.multiply(BigDecimal.ONE.add(absurdityRatio));
		BigDecimal p = BigDecimal.valueOf(price);
		return p.compareTo(lowerBand) < 0 || p.compareTo(upperBand) > 0;
	}
}
