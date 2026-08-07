package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Q-91(docs/91) — 제품 수동 보관 손잡이. 확정본(docs/90 §10) "제품 노화·만료 처리"가 지금까지
 * 구현·추적 어느 쪽도 안 돼 있었다 — 등록한 제품은 지울 수도 숨길 수도 없었다.
 *
 * <p>취소 가능(그대로 unarchive하면 목록에 복귀) — <b>표시 손잡이일 뿐</b>(절대 원칙 4), 매칭·폴링·알림
 * 로직은 이 값을 참조하지 않는다. 자동 시간 기반 만료(임계값이 정책 결정)는 이번 판에 안 연다.
 */
@Service
public class SetProductArchivedUseCase {

	private final ProductRepository products;

	public SetProductArchivedUseCase(ProductRepository products) {
		this.products = products;
	}

	@Transactional
	public void archive(long productId) {
		set(productId, true);
	}

	@Transactional
	public void unarchive(long productId) {
		set(productId, false);
	}

	private void set(long productId, boolean archived) {
		ProductEntity product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
		product.setArchived(archived);
	}
}
