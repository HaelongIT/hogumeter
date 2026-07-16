package dev.hogumeter.core.domain.deal;

import dev.hogumeter.core.domain.BenchmarkParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * BM-04 딜 병합·교차검증(순수 도메인). 병합 조건 = 동일 대상 + 가격 차 ≤ 허용폭 + 시간 차 ≤ 윈도.
 * 허용폭 = max(기존 딜가 × ratio, 절대 하한). 경계는 포함(≤). 병합 시 사이트 합집합으로 교차검증·상태 전이.
 * 수치는 주입받은 {@link BenchmarkParams}에서만 읽는다.
 */
public class DealMergePolicy {

	public boolean canMerge(DealEvent existing, DealEvent incoming, BenchmarkParams params) {
		return sameTarget(existing, incoming)
				&& priceWithinTolerance(existing, incoming, params)
				&& timeWithinWindow(existing, incoming, params.mergeWindowHours());
	}

	public DealEvent merge(DealEvent existing, DealEvent incoming) {
		DealEvent first = existing.firstSeen().isAfter(incoming.firstSeen()) ? incoming : existing;

		Set<String> sites = union(existing.sourceSites(), incoming.sourceSites());
		Set<Long> candidates = unionLongs(existing.productCandidates(), incoming.productCandidates());

		long priceMin = Math.min(existing.priceMin(), incoming.priceMin());
		long priceMax = Math.max(existing.priceMax(), incoming.priceMax());
		DealEvent last = existing.firstSeen().isAfter(incoming.firstSeen()) ? existing : incoming;

		Origin origin = (existing.origin() == Origin.LIVE || incoming.origin() == Origin.LIVE)
				? Origin.LIVE : Origin.BACKFILL;

		DealStatus status = existing.status();
		if (sites.size() >= 2 && status == DealStatus.ACTIVE) {
			status = DealStatus.VERIFIED; // 2번째 사이트 흡수 → 교차검증 전이
		}

		return new DealEvent(
				existing.variantId(),
				existing.unclassified(),
				candidates,
				first.priceFirst(),
				priceMin,
				priceMax,
				last.priceLast(),
				origin,
				sites,
				existing.outlierFlag(),
				existing.permanentlyExcluded() || incoming.permanentlyExcluded(),
				status,
				first.firstSeen(),
				last.lastSeen(),
				first.site(),
				first.sourceUrl(),
				union(existing.appliedConditions(), incoming.appliedConditions()),
				mergeDemandAxisValue(existing.demandAxisValue(), incoming.demandAxisValue()));
	}

	/**
	 * 병합된 딜의 수요축 값(Q-66 ①). 둘이 같으면 그 값, 한쪽만 알면 아는 값(다른 글이 색을 안 적었을 뿐이다).
	 * <b>서로 다르면 null = 미상</b> — 블랙 글과 화이트 글을 한 딜로 합쳤다면 어느 분포에 넣을지 알 수 없다.
	 * 하나를 골라 담으면 그 분포가 조용히 오염되므로, 지어내지 않고 사람에게 보낸다(확정본 §41).
	 */
	private static String mergeDemandAxisValue(String existing, String incoming) {
		if (existing == null) {
			return incoming;
		}
		if (incoming == null) {
			return existing;
		}
		return existing.equals(incoming) ? existing : null;
	}

	private static boolean sameTarget(DealEvent a, DealEvent b) {
		if (a.variantId() != null && b.variantId() != null) {
			return a.variantId().equals(b.variantId());
		}
		if (a.unclassified() && b.unclassified()) {
			return !Collections.disjoint(a.productCandidates(), b.productCandidates());
		}
		return false;
	}

	private static boolean priceWithinTolerance(DealEvent existing, DealEvent incoming, BenchmarkParams params) {
		long diff = Math.abs(existing.priceFirst() - incoming.priceFirst());
		long ratioAllowed = BigDecimal.valueOf(existing.priceFirst())
				.multiply(params.mergePriceToleranceRatio())
				.setScale(0, RoundingMode.HALF_UP).longValueExact();
		long allowed = Math.max(ratioAllowed, params.mergePriceToleranceFloorWon());
		return diff <= allowed;
	}

	private static boolean timeWithinWindow(DealEvent a, DealEvent b, int windowHours) {
		Duration diff = Duration.between(a.firstSeen(), b.firstSeen()).abs();
		return diff.compareTo(Duration.ofHours(windowHours)) <= 0;
	}

	private static Set<String> union(Set<String> a, Set<String> b) {
		Set<String> u = new HashSet<>(a);
		u.addAll(b);
		return u;
	}

	private static Set<Long> unionLongs(Set<Long> a, Set<Long> b) {
		Set<Long> u = new HashSet<>(a);
		u.addAll(b);
		return u;
	}
}
