package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.review.ReviewQueueItem;
import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** BM-07 사후학습 키워드 제안. 무시된 딜 빈출 토큰 → KEYWORD_SUGGEST(자동 반영 금지), 수락 시 제외셋 갱신. */
class KeywordSuggesterTest {

	private final KeywordSuggester suggester = new KeywordSuggester();
	private final ExcludeKeywordPolicy policy = new ExcludeKeywordPolicy();

	// ---- AC-1 "무시" → 빈출 토큰 후보 → KEYWORD_SUGGEST 큐행(자동 반영 없음) ----
	@Test
	void suggestsFrequentTokensFromIgnoredTitlesAsReviewItem() {
		List<String> ignored = List.of("갤럭시 약정 0원", "아이폰 약정 프로모션", "약정 특가 이벤트");

		Optional<ReviewQueueItem> item = suggester.suggest(ignored, Set.of());

		assertThat(item).isPresent();
		assertThat(item.get().type()).isEqualTo(ReviewQueueType.KEYWORD_SUGGEST);
		@SuppressWarnings("unchecked")
		List<String> candidates = (List<String>) item.get().payload().get("candidates");
		assertThat(candidates).contains("약정"); // 3건 공통 빈출
	}

	@Test
	void doesNotSuggestAlreadyKnownKeywords() {
		List<String> ignored = List.of("갤럭시 약정 0원", "아이폰 약정 프로모션");

		Optional<ReviewQueueItem> item = suggester.suggest(ignored, Set.of("약정"));

		assertThat(item).isEmpty(); // 이미 아는 키워드는 제안 안 함, 다른 빈출 없음
	}

	// ---- AC-2 수락 → 제외 키워드셋 반영 → 이후 BM-05 AC-4 규칙 적용 ----
	@Test
	void acceptedSuggestionUpdatesExcludeSetAndTakesEffect() {
		Set<String> updated = suggester.accept(Set.of(), "약정");
		assertThat(updated).contains("약정");

		// 반영 후엔 "약정" 포함 글이 제외된다(ExcludeKeywordPolicy 재사용)
		assertThat(policy.evaluate("갤럭시 약정 0원", updated, ExcludeKeywordPolicy.Mode.EXCLUDE))
				.isEqualTo(ExcludeKeywordPolicy.Verdict.EXCLUDED);
	}
}
