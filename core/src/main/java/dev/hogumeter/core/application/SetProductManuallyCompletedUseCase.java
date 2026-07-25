package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRI ②축소(docs/19) — "수동 완료"(중고·장외 구매 이탈용) 손잡이. 취소 가능(그대로 false로 되돌리면
 * 대기열에 복귀) — 딜 알림은 이 값과 무관하게 계속 유지된다("알림은 유지됨" 고지, 이 유스케이스는 그
 * 표시 손잡이만 다루고 알림 정책엔 손대지 않는다).
 */
@Service
public class SetProductManuallyCompletedUseCase {

	private final ProductRepository products;

	public SetProductManuallyCompletedUseCase(ProductRepository products) {
		this.products = products;
	}

	@Transactional
	public void set(long productId, boolean manuallyCompleted) {
		ProductEntity product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
		product.setManuallyCompleted(manuallyCompleted);
	}
}
