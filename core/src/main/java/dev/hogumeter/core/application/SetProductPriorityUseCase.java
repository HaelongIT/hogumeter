package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRI ②축소(docs/19) — 우선순위 순번 설정. "유일 순번"이라 DB 부분 유니크 인덱스(V18)가 최종 방어선이고,
 * 이 유스케이스는 그 위반을 사람이 읽는 예외로 바꾼다. {@code null}은 "미지정"(해제) — 부분 인덱스가
 * null끼리는 충돌시키지 않으므로 그대로 허용한다.
 */
@Service
public class SetProductPriorityUseCase {

	private final ProductRepository products;

	public SetProductPriorityUseCase(ProductRepository products) {
		this.products = products;
	}

	@Transactional
	public void setPriority(long productId, Integer rank) {
		ProductEntity product = products.findById(productId).orElseThrow(() -> new ProductNotFoundException(productId));
		product.setPriorityRank(rank);
		try {
			products.saveAndFlush(product);
		}
		catch (DataIntegrityViolationException duplicate) {
			throw new DuplicatePriorityRankException(rank);
		}
	}
}
