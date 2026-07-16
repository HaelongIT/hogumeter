package dev.hogumeter.core.domain.matching;

import java.util.List;
import java.util.Set;

/**
 * 매칭용 제품 명세 — productId + 부분 일치 판정용 코어 토큰(예: {"아이폰","17"}) + variant 명세들.
 * 별칭 사전(학습된 표현)과 별개로 제품 고유 토큰을 제공한다. registration 모듈 소유 개체의 매칭 투영.
 *
 * @param demandAxis 수요축(색상 등) 명세. <b>없으면 {@code null}</b> — 수요축을 등록하지 않은 제품이 대부분이다.
 *     있으면 매칭이 제목에서 그 값을 판별해 딜에 실어 준다(Q-66 ①). 값 판별은 SPLIT일 때만 분포를 가르지만,
 *     판별 자체는 모드와 무관하게 한다 — 나중에 묶음→분리로 바꿔도 과거 딜의 값이 남아 있어야 한다.
 */
public record ProductMatchSpec(long productId, Set<String> coreTokens, List<VariantSpec> variants,
		DemandAxisSpec demandAxis) {

	public ProductMatchSpec {
		coreTokens = Set.copyOf(coreTokens);
		variants = List.copyOf(variants);
	}

	/** 수요축이 없는 제품(대부분) — 기존 호출자를 위한 편의. */
	public ProductMatchSpec(long productId, Set<String> coreTokens, List<VariantSpec> variants) {
		this(productId, coreTokens, variants, null);
	}
}
