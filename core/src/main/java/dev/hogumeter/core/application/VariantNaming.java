package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * variantId → 사람이 읽는 이름(제품명·variant 라벨). 알림 본문(AL-05)이 "무엇이 특가인가"를 말하려면
 * 필요한데 {@code DealEvent}·{@code AlertMessage}는 variantId만 지녀 이름이 없다. 여기서 한 번 조회해 채운다.
 *
 * <p><b>못 찾으면 지어내지 않는다</b> — {@link Naming#UNKNOWN}(둘 다 null)을 돌려주고, 포맷터가 그 사실을
 * "대상 미상"으로 그린다(과대약속 금지). variant가 지워졌거나 매칭이 어긋난 딜에도 알림은 근거 링크로 넘긴다.
 */
@Service
public class VariantNaming {

	private final VariantRepository variants;
	private final ProductRepository products;

	public VariantNaming(VariantRepository variants, ProductRepository products) {
		this.variants = variants;
		this.products = products;
	}

	@Transactional(readOnly = true)
	public Naming of(long variantId) {
		return variants.findById(variantId)
				.map(v -> new Naming(
						products.findById(v.getProductId()).map(ProductEntity::getName).orElse(null),
						v.getLabel()))
				.orElse(Naming.UNKNOWN);
	}

	/** 제품명·variant 라벨. 둘 중 하나라도 없으면 null — 포맷터가 "대상 미상"으로 다룬다. */
	public record Naming(String productName, String variantLabel) {

		public static final Naming UNKNOWN = new Naming(null, null);
	}
}
