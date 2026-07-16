package dev.hogumeter.core.domain.matching;

import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.Map;
import java.util.Set;

/**
 * 매칭 결과(순수 값). CONFIRMED는 variantId, CANDIDATE는 후보군+UNCLASSIFIED 리뷰 항목,
 * UNKNOWN(미상)은 후보군만, REJECTED는 무. reviewItem은 CANDIDATE에서만 존재(nullable).
 *
 * @param demandAxisValue 제목에서 판별한 수요축 값(Q-66 ①). CONFIRMED에서만 의미가 있고,
 *     <b>{@code null}이면 "값 미상"</b>이다 — 수요축이 없는 제품이거나, 제목에 값이 없거나, 둘 이상 보여
 *     어느 것인지 모르는 경우다(확정본 §41). 셋을 굳이 구분하지 않는 이유: 어느 쪽이든 SPLIT 분포에
 *     넣을 수 없다는 결론이 같다.
 */
public record MatchResult(MatchTier tier, Long variantId, Set<Long> productCandidates, ReviewQueueItem reviewItem,
		String demandAxisValue) {

	public MatchResult {
		productCandidates = Set.copyOf(productCandidates);
	}

	public static MatchResult confirmed(long variantId) {
		return confirmed(variantId, null);
	}

	public static MatchResult confirmed(long variantId, String demandAxisValue) {
		return new MatchResult(MatchTier.CONFIRMED, variantId, Set.of(), null, demandAxisValue);
	}

	public static MatchResult candidate(Set<Long> candidates) {
		return new MatchResult(MatchTier.CANDIDATE, null, candidates,
				new ReviewQueueItem(ReviewQueueType.UNCLASSIFIED, Map.of("productCandidates", candidates)), null);
	}

	public static MatchResult unknown(Set<Long> candidates) {
		return new MatchResult(MatchTier.UNKNOWN, null, candidates, null, null);
	}

	public static MatchResult rejected() {
		return new MatchResult(MatchTier.REJECTED, null, Set.of(), null, null);
	}
}
