package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.matching.AliasDictionary;
import dev.hogumeter.core.domain.matching.ProductMatchSpec;
import dev.hogumeter.core.domain.matching.TitleNormalizer;
import dev.hogumeter.core.domain.matching.VariantSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 저장된 제품·variant·별칭을 매칭 도메인 입력으로 투영. 제품 코어 토큰 = 제품명 정규화 토큰,
 * variant 축값 = price_axis_values의 값(정규화), 별칭 = 정규화·공백제거 표현.
 */
@Component
public class CatalogProjection {

	private final ProductRepository products;
	private final VariantRepository variants;
	private final AliasRepository aliases;

	public CatalogProjection(ProductRepository products, VariantRepository variants, AliasRepository aliases) {
		this.products = products;
		this.variants = variants;
		this.aliases = aliases;
	}

	public List<ProductMatchSpec> catalog() {
		return products.findAll().stream()
				.map(p -> new ProductMatchSpec(
						p.getId(),
						TitleNormalizer.tokens(p.getName()),
						variants.findByProductId(p.getId()).stream()
								.map(CatalogProjection::toVariantSpec)
								.toList()))
				.toList();
	}

	public AliasDictionary aliasDictionary() {
		Map<String, Long> map = new HashMap<>();
		for (AliasEntity a : aliases.findAll()) {
			if (a.getProductId() != null) { // 전역 별칭(product_id null)은 이 슬라이스 범위 밖
				map.put(TitleNormalizer.joined(a.getAlias()), a.getProductId());
			}
		}
		return AliasDictionary.of(map);
	}

	private static VariantSpec toVariantSpec(VariantEntity v) {
		Set<String> axisValues = v.getPriceAxisValues().values().stream()
				.map(TitleNormalizer::joined)
				.collect(Collectors.toSet());
		return new VariantSpec(v.getId(), axisValues);
	}
}
