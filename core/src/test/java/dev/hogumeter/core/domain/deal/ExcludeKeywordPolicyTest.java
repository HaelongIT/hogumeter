package dev.hogumeter.core.domain.deal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** BM-05 AC-4 제외 키워드 — 배제(기본) vs ⚠️라벨만 토글. 키워드 판정은 가격·이상치와 독립. */
class ExcludeKeywordPolicyTest {

	private final ExcludeKeywordPolicy policy = new ExcludeKeywordPolicy();
	private final Set<String> excludeKeywords = Set.of("약정", "중고");

	@Test
	void keywordHitWithExcludeModeIsExcluded() {
		assertThat(policy.evaluate("아이폰 17 약정 가입가", excludeKeywords, ExcludeKeywordPolicy.Mode.EXCLUDE))
				.isEqualTo(ExcludeKeywordPolicy.Verdict.EXCLUDED);
	}

	@Test
	void keywordHitWithLabelModeIsLabeled() {
		assertThat(policy.evaluate("아이폰 17 약정 가입가", excludeKeywords, ExcludeKeywordPolicy.Mode.LABEL))
				.isEqualTo(ExcludeKeywordPolicy.Verdict.LABELED);
	}

	@Test
	void noKeywordHitIsClean() {
		assertThat(policy.evaluate("아이폰 17 자급제 정상가", excludeKeywords, ExcludeKeywordPolicy.Mode.EXCLUDE))
				.isEqualTo(ExcludeKeywordPolicy.Verdict.CLEAN);
	}

	@Test
	void keywordExclusionIsIndependentOfPrice() {
		// AC-4 교차 케이스: "약정 0원"은 가격상 LOWER지만 키워드로 배제된다
		assertThat(policy.evaluate("갤럭시 약정 0원", excludeKeywords, ExcludeKeywordPolicy.Mode.EXCLUDE))
				.isEqualTo(ExcludeKeywordPolicy.Verdict.EXCLUDED);
	}
}
