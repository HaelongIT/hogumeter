package dev.hogumeter.core.domain.matching;

import java.util.List;
import java.util.Set;

/**
 * 매칭용 제품 명세 — productId + 부분 일치 판정용 코어 토큰(예: {"아이폰","17"}) + variant 명세들.
 * 별칭 사전(학습된 표현)과 별개로 제품 고유 토큰을 제공한다. registration 모듈 소유 개체의 매칭 투영.
 */
public record ProductMatchSpec(long productId, Set<String> coreTokens, List<VariantSpec> variants) {

	public ProductMatchSpec {
		coreTokens = Set.copyOf(coreTokens);
		variants = List.copyOf(variants);
	}
}
