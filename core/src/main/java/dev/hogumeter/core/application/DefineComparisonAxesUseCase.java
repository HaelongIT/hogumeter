package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ComparisonAxisEntity;
import dev.hogumeter.core.adapter.persistence.ComparisonAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * USED-05 AC-17 ① — 비교축 정의(제품 단위, {@code comparison_axis}). <b>추가 전용</b>이다: 이미 정의된
 * 축에 값이 승격돼 있을 수 있고({@code listing_axis_value}가 axis_id를 FK로 참조), 축을 지우면 그 값이
 * FK 위반으로 막히거나(운영 편의를 해치거나) cascade로 조용히 사라진다. 되돌리기 쉬운 쪽(추가만, 삭제
 * 없음)을 택했다 — 필요해지면 축 삭제는 별도 명시적 엔드포인트로 분리한다.
 */
@Service
public class DefineComparisonAxesUseCase {

	private final ComparisonAxisRepository axes;
	private final ProductRepository products;

	public DefineComparisonAxesUseCase(ComparisonAxisRepository axes, ProductRepository products) {
		this.axes = axes;
		this.products = products;
	}

	/**
	 * 이름이 없는 축만 추가한다. 반환값은 이 호출 뒤 그 제품의 축 전체(멱등 조회 편의).
	 *
	 * <p>BE-14(코드리뷰 20260806): 없는 productId는 FK 위반(500)이 아니라 404로 거절한다 — 다른
	 * 유스케이스({@code RegisterProductUseCase} 등)가 쓰는 선검사와 같은 계약.
	 */
	public List<ComparisonAxisEntity> ensure(long productId, List<String> names) {
		if (!products.existsById(productId)) {
			throw new ProductNotFoundException(productId);
		}
		for (String name : names) {
			if (axes.findByProductIdAndName(productId, name).isEmpty()) {
				axes.save(new ComparisonAxisEntity(productId, name));
			}
		}
		return axes.findByProductId(productId);
	}
}
