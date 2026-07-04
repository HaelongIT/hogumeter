package dev.hogumeter.core.domain.matching;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.review.ReviewQueueType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * BM-03 v1 탈모델 매칭(임베딩·LLM 금지). 별칭 사전 substring 히트 → 제품 확정, variant 축값 토큰으로 배정.
 * 부분 일치는 REJECTED가 아니라 CANDIDATE(재현율 우선). 축값 없으면 미상 버킷.
 */
class MatcherTest {

	private final Matcher matcher = new Matcher();

	private final ProductMatchSpec iphone = new ProductMatchSpec(1L, Set.of("아이폰", "17"),
			List.of(new VariantSpec(10L, Set.of("256GB")), new VariantSpec(11L, Set.of("512GB"))));
	private final List<ProductMatchSpec> catalog = List.of(iphone);
	private final AliasDictionary dict = AliasDictionary.of(Map.of("아이폰17", 1L));

	// ---- AC-1 별칭 히트 → CONFIRMED + variant 배정 ----
	@Test
	void aliasHitConfirmsAndAssignsVariant() {
		MatchResult r = matcher.match("아이폰 17 256기가 자급제 89만", catalog, dict);

		assertThat(r.tier()).isEqualTo(MatchTier.CONFIRMED);
		assertThat(r.variantId()).isEqualTo(10L);
		assertThat(r.reviewItem()).isNull();
	}

	// ---- AC-2 부분 일치 → CANDIDATE + reviewQueue(UNCLASSIFIED) ----
	@Test
	void partialProductMatchBecomesCandidateNotRejected() {
		MatchResult r = matcher.match("애플 아이폰 신형 256기가", catalog, dict); // "17" 없음 → 별칭 미히트, 부분만

		assertThat(r.tier()).isEqualTo(MatchTier.CANDIDATE);
		assertThat(r.productCandidates()).containsExactly(1L);
		assertThat(r.reviewItem()).isNotNull();
		assertThat(r.reviewItem().type()).isEqualTo(ReviewQueueType.UNCLASSIFIED);
	}

	// ---- AC-3 제품은 확실하나 축값 없음 → 미상 버킷 ----
	@Test
	void productKnownButNoAxisTokenGoesToUnknownBucket() {
		MatchResult r = matcher.match("아이폰 17 자급제 특가", catalog, dict); // 용량 토큰 없음

		assertThat(r.tier()).isEqualTo(MatchTier.UNKNOWN);
		assertThat(r.variantId()).isNull();
		assertThat(r.productCandidates()).containsExactly(1L);
	}

	// ---- 아무 토큰도 안 겹침 → REJECTED ----
	@Test
	void noOverlapIsRejected() {
		MatchResult r = matcher.match("갤럭시 S25 울트라 512기가", catalog, dict);

		assertThat(r.tier()).isEqualTo(MatchTier.REJECTED);
	}

	// ---- AC-4 사람 확정 → 별칭 자동 축적 → 재매칭 CONFIRMED ----
	@Test
	void humanConfirmationAccumulatesAliasSoNextMatchIsConfirmed() {
		AliasDictionary empty = AliasDictionary.of(Map.of());
		String title = "애플 아이폰 최신형 256기가";

		MatchResult before = matcher.match(title, catalog, empty);
		assertThat(before.tier()).isEqualTo(MatchTier.CANDIDATE);

		AliasDictionary learned = matcher.confirm(empty, title, 1L); // 사람이 product-1로 확정
		MatchResult after = matcher.match(title, catalog, learned);

		assertThat(after.tier()).isEqualTo(MatchTier.CONFIRMED);
		assertThat(after.variantId()).isEqualTo(10L);
	}
}
