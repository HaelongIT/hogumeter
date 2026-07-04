package dev.hogumeter.core.domain.matching;

import java.util.Set;

/**
 * 매칭용 variant 명세 — variantId + 이 variant를 식별하는 (정규화된) 축값 토큰들(예: {"256GB"}).
 * 제목(joined)이 axisValues를 모두 포함하면 이 variant로 배정.
 */
public record VariantSpec(long variantId, Set<String> axisValues) {

	public VariantSpec {
		axisValues = Set.copyOf(axisValues);
	}
}
