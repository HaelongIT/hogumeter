package dev.hogumeter.core.domain.deal;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * BM-01 AC-2 "상태 변화는 기존 행에 반영" 중 <b>가격 변경</b> 몫(docs/91 Q-27 ①). 순수 산술.
 *
 * <p>가격 역할 3분법(v1.3, docs/02): {@code priceFirst}는 발생·분포용이라 <b>절대 바뀌지 않는다</b> —
 * 기준가 median·percentile이 그 위에 서 있다. 여기서 움직이는 것은 "지금"({@code priceLast}),
 * "지나간 기회"({@code priceMin}), 그리고 역할 없는 {@code priceMax}뿐이다.
 *
 * <p>{@code lastSeen}은 <b>단조</b>다. 늦게 도착한 과거 관측이 시계를 되돌리면 안 된다.
 */
public record PriceRefresh(long priceMin, long priceMax, long priceLast, Instant lastSeen) {

	/**
	 * 바뀔 것이 없으면 {@link Optional#empty()}. 빈 갱신을 쓰면 {@code lastSeen}만 흔들려
	 * "언제 마지막으로 실제로 변했는가"를 잃는다.
	 */
	public static Optional<PriceRefresh> from(DealEvent current, List<PriceEvidence> evidence) {
		if (evidence.isEmpty()) {
			return Optional.empty();
		}

		long priceMin = Math.min(current.priceMin(),
				evidence.stream().mapToLong(PriceEvidence::price).min().orElseThrow());
		long priceMax = Math.max(current.priceMax(),
				evidence.stream().mapToLong(PriceEvidence::price).max().orElseThrow());

		// "지금"은 아직 살 수 있는 원문 중 가장 최근에 본 값. 동시각이면 더 싼 쪽(사용자에게 유리하게).
		long priceLast = evidence.stream()
				.filter(PriceEvidence::active)
				.max(Comparator.comparing(PriceEvidence::capturedAt)
						.thenComparing(Comparator.comparingLong(PriceEvidence::price).reversed()))
				.map(PriceEvidence::price)
				.orElse(current.priceLast());

		Instant newestEvidence = evidence.stream()
				.map(PriceEvidence::capturedAt)
				.max(Comparator.naturalOrder())
				.orElseThrow();
		Instant lastSeen = newestEvidence.isAfter(current.lastSeen()) ? newestEvidence : current.lastSeen();

		boolean unchanged = priceMin == current.priceMin()
				&& priceMax == current.priceMax()
				&& priceLast == current.priceLast()
				&& lastSeen.equals(current.lastSeen());

		return unchanged ? Optional.empty() : Optional.of(new PriceRefresh(priceMin, priceMax, priceLast, lastSeen));
	}
}
