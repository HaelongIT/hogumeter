package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.priority.PriorityQueue;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRI ②축소(docs/19) — 우선순위 정렬 목록(표면 = 목록 정렬 옵션 1곳). 대기 중인 제품을 순번 순으로
 * 먼저(미지정은 뒤로), 비대기 제품은 그 뒤에 낸다 — 정렬 자체가 "대기열 순서 상기"라는 표방이다.
 *
 * <p>1인용 규모라 N+1(variant→purchase)을 그대로 둔다(과최적화 금지, PERF-04 — `GetReviewQueueUseCase`와
 * 같은 판단).
 */
@Service
public class GetPrioritizedProductsUseCase {

	private final ProductRepository products;
	private final VariantRepository variants;
	private final PurchaseRepository purchases;

	public GetPrioritizedProductsUseCase(ProductRepository products, VariantRepository variants,
			PurchaseRepository purchases) {
		this.products = products;
		this.variants = variants;
		this.purchases = purchases;
	}

	@Transactional(readOnly = true)
	public List<PrioritizedProduct> list() {
		return products.findAll().stream()
				.map(this::toItem)
				.sorted(Comparator.comparing((PrioritizedProduct p) -> !p.waiting())
						.thenComparing(p -> p.priorityRank() == null ? Integer.MAX_VALUE : p.priorityRank())
						.thenComparing(PrioritizedProduct::productId))
				.toList();
	}

	private PrioritizedProduct toItem(ProductEntity product) {
		boolean hasAnyPurchase = variants.findByProductId(product.getId()).stream()
				.anyMatch(v -> !purchases.findByVariantId(v.getId()).isEmpty());
		boolean waiting = PriorityQueue.isWaiting(hasAnyPurchase, product.isManuallyCompleted());
		return new PrioritizedProduct(product.getId(), product.getName(), product.getPriorityRank(), waiting,
				product.isManuallyCompleted());
	}

	/**
	 * @param priorityRank 사용자가 지정한 순번. 미지정이면 {@code null}(정렬은 맨 뒤로 민다).
	 * @param waiting {@link PriorityQueue#isWaiting} 결과 — 대기 중인가.
	 * @param manuallyCompleted 사용자가 수동 완료로 표시했는가(취소 가능).
	 */
	public record PrioritizedProduct(long productId, String name, Integer priorityRank, boolean waiting,
			boolean manuallyCompleted) {
	}
}
