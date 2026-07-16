package dev.hogumeter.core.adapter.persistence;

import dev.hogumeter.core.domain.matching.AliasDictionary;
import dev.hogumeter.core.domain.matching.DemandAxisSpec;
import dev.hogumeter.core.domain.matching.ProductMatchSpec;
import dev.hogumeter.core.domain.matching.TitleNormalizer;
import dev.hogumeter.core.domain.matching.VariantSpec;
import dev.hogumeter.core.domain.product.AxisType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 저장된 제품·variant·별칭을 매칭 도메인 입력으로 투영. 제품 코어 토큰 = 제품명 정규화 토큰,
 * variant 축값 = price_axis_values의 값(정규화), 별칭 = 정규화·공백제거 표현.
 *
 * <p>수요축(Q-66 ①)도 함께 투영한다 — 매칭이 제목에서 그 값을 판별해 딜에 실어야 SPLIT 분포를 가를 수 있다.
 */
@Component
public class CatalogProjection {

	private final ProductRepository products;
	private final VariantRepository variants;
	private final AliasRepository aliases;
	private final ProductAxisRepository axes;

	public CatalogProjection(ProductRepository products, VariantRepository variants, AliasRepository aliases,
			ProductAxisRepository axes) {
		this.products = products;
		this.variants = variants;
		this.aliases = aliases;
		this.axes = axes;
	}

	public List<ProductMatchSpec> catalog() {
		return products.findAll().stream()
				.map(p -> new ProductMatchSpec(
						p.getId(),
						TitleNormalizer.tokens(p.getName()),
						variants.findByProductId(p.getId()).stream()
								.map(CatalogProjection::toVariantSpec)
								.toList(),
						demandAxisOf(p.getId())))
				.toList();
	}

	/**
	 * 수요축은 <b>많아야 하나</b>로 본다(DDL의 {@code unique (product_id, axis_type, name)}는 이름이 다르면
	 * 둘을 허용하지만, 확정본 §38의 수요축은 "묶음/분리 토글" 하나를 전제한다). 둘 이상이면 첫 번째만 쓰고
	 * 나머지는 무시한다 — 지금 그 상황을 만들 UI가 없다(등록 화면은 유형만 고르게 한다).
	 *
	 * @return 수요축이 없으면 {@code null}(대부분의 제품).
	 */
	private DemandAxisSpec demandAxisOf(long productId) {
		return axes.findByProductId(productId).stream()
				.filter(axis -> axis.getAxisType() == AxisType.DEMAND)
				.findFirst()
				.map(axis -> DemandAxisSpec.of(axis.getName(), axis.getAllowedValues()))
				.orElse(null);
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
