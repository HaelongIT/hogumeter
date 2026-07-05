package dev.hogumeter.core.domain.deal;

import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * BM-07 사후학습 키워드 제안(순수 도메인). 무시된 딜 제목들에서 여러 건에 걸쳐 빈출하는 토큰을
 * 제외 키워드 후보로 뽑아 KEYWORD_SUGGEST 리뷰 항목을 만든다. "판단은 사람" — 자동 반영은 없다(수락 시에만 갱신).
 */
public class KeywordSuggester {

	private static final long MIN_FREQUENCY = 2; // 여러 무시 건에 공통으로 나타난 토큰만 후보

	public Optional<ReviewQueueItem> suggest(List<String> ignoredTitles, Set<String> known) {
		Map<String, Long> frequency = new HashMap<>();
		for (String title : ignoredTitles) {
			for (String token : new HashSet<>(tokenize(title))) { // 제목당 1회만 카운트
				if (isCandidate(token, known)) {
					frequency.merge(token, 1L, Long::sum);
				}
			}
		}
		List<String> candidates = frequency.entrySet().stream()
				.filter(e -> e.getValue() >= MIN_FREQUENCY)
				.map(Map.Entry::getKey)
				.sorted()
				.toList();
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ReviewQueueItem(ReviewQueueType.KEYWORD_SUGGEST, Map.of("candidates", candidates)));
	}

	/** 사람이 수락한 키워드를 제외 키워드셋에 반영한 새 셋(불변). 이후 BM-05 AC-4 규칙을 따른다. */
	public Set<String> accept(Set<String> currentExclude, String keyword) {
		Set<String> next = new HashSet<>(currentExclude);
		next.add(keyword);
		return next;
	}

	private static boolean isCandidate(String token, Set<String> known) {
		return token.length() >= 2 && !known.contains(token) && token.matches(".*[가-힣A-Za-z].*");
	}

	private static List<String> tokenize(String title) {
		return Arrays.asList(title.trim().split("\\s+"));
	}
}
