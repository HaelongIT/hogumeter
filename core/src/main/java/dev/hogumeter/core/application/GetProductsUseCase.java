package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductAxisEntity;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 제품·variant 조회(REG, 읽기 전용). 저장하지 않고 매 조회 시 읽는다.
 *
 * <p>왜 필요한가: 등록 응답은 {@code productId}만 돌려주는데 기준가·신호·주기·구매 조회는 전부
 * {@code variantId}를 요구한다. 이 조회가 없으면 클라이언트는 자기가 만든 variant로 돌아갈 수 없다.
 *
 * <p>1인용 규모라 페이지네이션 없음(과최적화 금지, PERF-04).
 */
@Service
public class GetProductsUseCase {

	private final ProductRepository products;
	private final VariantRepository variants;
	private final ProductAxisRepository axes;

	public GetProductsUseCase(ProductRepository products, VariantRepository variants, ProductAxisRepository axes) {
		this.products = products;
		this.variants = variants;
		this.axes = axes;
	}

	@Transactional(readOnly = true)
	public List<ProductSummary> listProducts() {
		return products.findAll().stream().map(this::summarize).toList();
	}

	@Transactional(readOnly = true)
	public List<VariantView> variantsOf(long productId) {
		return variants.findByProductId(productId).stream().map(GetProductsUseCase::toView).toList();
	}

	private ProductSummary summarize(ProductEntity product) {
		return new ProductSummary(product.getId(), product.getName(), product.getCategory(),
				product.getDemandAxisMode(), axesOf(product.getId()), variantsOf(product.getId()));
	}

	/**
	 * 축 정의(Q-66 ②). {@code product_axis}는 등록이 쓰기만 하고 <b>아무도 읽지 않는 테이블</b>이었다 —
	 * 그래서 사람은 자기가 어느 축을 수요축으로 등록했는지 화면에서 확인할 길이 없었다. variant 라벨은
	 * 가격축 조합만 보여 주므로 수요축은 흔적조차 없다.
	 */
	private List<AxisView> axesOf(long productId) {
		return axes.findByProductId(productId).stream()
				.map(axis -> new AxisView(axis.getAxisType(), axis.getName(), axis.getAllowedValues()))
				.toList();
	}

	private static VariantView toView(VariantEntity variant) {
		return new VariantView(variant.getId(), variant.getLabel(), variant.getPriceAxisValues());
	}

	public record ProductSummary(long productId, String name, String category, DemandAxisMode demandAxisMode,
			List<AxisView> axes, List<VariantView> variants) {
	}

	/** 축 하나의 정의. 가격축은 variant를 나누고, 수요축은 나누지 않는다(확정본 §38). */
	public record AxisView(AxisType axisType, String name, List<String> allowedValues) {
	}

	public record VariantView(long variantId, String label, Map<String, String> priceAxisValues) {
	}
}
