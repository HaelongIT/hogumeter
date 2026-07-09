package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
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

	public GetProductsUseCase(ProductRepository products, VariantRepository variants) {
		this.products = products;
		this.variants = variants;
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
				product.getDemandAxisMode(), variantsOf(product.getId()));
	}

	private static VariantView toView(VariantEntity variant) {
		return new VariantView(variant.getId(), variant.getLabel(), variant.getPriceAxisValues());
	}

	public record ProductSummary(long productId, String name, String category, DemandAxisMode demandAxisMode,
			List<VariantView> variants) {
	}

	public record VariantView(long variantId, String label, Map<String, String> priceAxisValues) {
	}
}
