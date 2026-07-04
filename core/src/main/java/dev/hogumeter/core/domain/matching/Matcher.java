package dev.hogumeter.core.domain.matching;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BM-03 v1 탈모델 매칭(순수 도메인, 임베딩·LLM 금지).
 * 1) 별칭 사전 substring 히트 → 제품 확정 → variant 축값 토큰으로 배정(CONFIRMED) / 축값 없으면 미상(UNKNOWN).
 * 2) 별칭 미히트 → 코어 토큰 부분 일치면 CANDIDATE(재현율 우선, 놓침>오알림) / 무관이면 REJECTED.
 * 사람 확정(confirm)은 표현을 별칭 사전에 축적한다(AC-4).
 */
public class Matcher {

	public MatchResult match(String title, List<ProductMatchSpec> catalog, AliasDictionary dictionary) {
		String joined = TitleNormalizer.joined(title);
		Set<String> tokens = TitleNormalizer.tokens(title);

		Optional<Long> productId = dictionary.match(joined);
		if (productId.isPresent()) {
			ProductMatchSpec spec = find(catalog, productId.get());
			if (spec == null) {
				return MatchResult.unknown(Set.of(productId.get()));
			}
			List<VariantSpec> matches = spec.variants().stream()
					.filter(v -> v.axisValues().stream().allMatch(joined::contains))
					.toList();
			if (matches.size() == 1) {
				return MatchResult.confirmed(matches.get(0).variantId());
			}
			return MatchResult.unknown(Set.of(spec.productId())); // 축값 판별 불가/모호 → 미상
		}

		Set<Long> candidates = catalog.stream()
				.filter(s -> s.coreTokens().stream().anyMatch(tokens::contains))
				.map(ProductMatchSpec::productId)
				.collect(Collectors.toCollection(java.util.TreeSet::new));
		if (candidates.isEmpty()) {
			return MatchResult.rejected();
		}
		return MatchResult.candidate(candidates);
	}

	/** 사람이 title을 productId로 확정 → 표현을 별칭 사전에 축적(AC-4). */
	public AliasDictionary confirm(AliasDictionary dictionary, String title, long productId) {
		return dictionary.learn(TitleNormalizer.joined(title), productId);
	}

	private static ProductMatchSpec find(List<ProductMatchSpec> catalog, long productId) {
		return catalog.stream().filter(s -> s.productId() == productId).findFirst().orElse(null);
	}
}
