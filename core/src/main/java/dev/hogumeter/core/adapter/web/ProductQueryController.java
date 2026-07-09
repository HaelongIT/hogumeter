package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetProductsUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 제품·variant 조회 REST(REG, 읽기 전용). 봉투 없는 리소스 직접 반환(Q-2 확정). */
@RestController
public class ProductQueryController {

	private final GetProductsUseCase getProducts;

	public ProductQueryController(GetProductsUseCase getProducts) {
		this.getProducts = getProducts;
	}

	@GetMapping("/api/v1/products")
	public List<GetProductsUseCase.ProductSummary> products() {
		return getProducts.listProducts();
	}

	/** 없는 제품이면 빈 목록 — 404가 아니다. "이 제품의 variant 집합"은 공집합일 수 있다. */
	@GetMapping("/api/v1/products/{productId}/variants")
	public List<GetProductsUseCase.VariantView> variants(@PathVariable long productId) {
		return getProducts.variantsOf(productId);
	}
}
