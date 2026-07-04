package dev.hogumeter.core.domain.matching;

import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.Map;
import java.util.Set;

/**
 * 매칭 결과(순수 값). CONFIRMED는 variantId, CANDIDATE는 후보군+UNCLASSIFIED 리뷰 항목,
 * UNKNOWN(미상)은 후보군만, REJECTED는 무. reviewItem은 CANDIDATE에서만 존재(nullable).
 */
public record MatchResult(MatchTier tier, Long variantId, Set<Long> productCandidates, ReviewQueueItem reviewItem) {

	public MatchResult {
		productCandidates = Set.copyOf(productCandidates);
	}

	public static MatchResult confirmed(long variantId) {
		return new MatchResult(MatchTier.CONFIRMED, variantId, Set.of(), null);
	}

	public static MatchResult candidate(Set<Long> candidates) {
		return new MatchResult(MatchTier.CANDIDATE, null, candidates,
				new ReviewQueueItem(ReviewQueueType.UNCLASSIFIED, Map.of("productCandidates", candidates)));
	}

	public static MatchResult unknown(Set<Long> candidates) {
		return new MatchResult(MatchTier.UNKNOWN, null, candidates, null);
	}

	public static MatchResult rejected() {
		return new MatchResult(MatchTier.REJECTED, null, Set.of(), null);
	}
}
