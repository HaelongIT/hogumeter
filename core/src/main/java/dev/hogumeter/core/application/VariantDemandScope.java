package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.ProductAxisEntity;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.benchmark.VariantNotFoundException;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * variant의 수요축 범위(Q-66 ①, 확정본 §40·41). 이 variant가 속한 제품이 <b>묶음이냐 분리냐</b>를 읽고,
 * 분리면 요청된 축 값으로 표본을 좁힌다.
 *
 * <p><b>해석은 여기 한 곳에만 둔다.</b> 기준가·신호등이 각자 "분리면 어떻게 하나"를 해석하면 한쪽이
 * 조용히 다른 표본을 본다 — 기준가는 블랙만 보는데 신호등은 전부 보는 식이면 같은 화면이 서로 다른
 * 사실을 말한다. 순수 규칙은 {@link DealSets#demandScope}이고 여기는 모드를 읽어 그걸 부르는 IO다.
 */
@Service
public class VariantDemandScope {

	private final VariantRepository variants;
	private final ProductRepository products;
	private final ProductAxisRepository axes;

	public VariantDemandScope(VariantRepository variants, ProductRepository products, ProductAxisRepository axes) {
		this.variants = variants;
		this.products = products;
		this.axes = axes;
	}

	/**
	 * 이 variant의 표본을 수요축 범위로 좁힌다.
	 *
	 * @param demandAxisValue 분리 제품에서 볼 축 값. 묶음이면 무시한다(값을 보내도 분포를 가르지 않는다).
	 * @throws DemandAxisValueRequiredException 분리 제품인데 값이 없으면 — 전체로 답하면 그게 묶음의 거짓말이다.
	 */
	public List<DealEvent> scope(long variantId, List<DealEvent> deals, String demandAxisValue) {
		if (modeOf(variantId) != DemandAxisMode.SPLIT) {
			return deals;
		}
		if (demandAxisValue == null || demandAxisValue.isBlank()) {
			throw new DemandAxisValueRequiredException(demandAxisName(variantId));
		}
		return DealSets.demandScope(deals, DemandAxisMode.SPLIT, demandAxisValue);
	}

	public DemandAxisMode modeOf(long variantId) {
		return product(variantId).getDemandAxisMode();
	}

	/**
	 * 분리 제품인데 값이 없으면 거절한다(Q-66 ③). 표본을 좁히지 않고 <b>검증만</b> 하는 자리 — 구매 기록처럼
	 * "딜을 거르는" 게 아니라 "입력이 온전한가"를 묻는 호출자를 위해.
	 */
	public void requireValueWhenSplit(long variantId, String demandAxisValue) {
		if (modeOf(variantId) == DemandAxisMode.SPLIT && (demandAxisValue == null || demandAxisValue.isBlank())) {
			throw new DemandAxisValueRequiredException(demandAxisName(variantId));
		}
	}

	/** 사람에게 "어느 축 값을 지정하라"고 말하려면 축 이름이 필요하다. 없으면 일반 이름으로 답한다. */
	private String demandAxisName(long variantId) {
		return axes.findByProductId(product(variantId).getId()).stream()
				.filter(axis -> axis.getAxisType() == AxisType.DEMAND)
				.findFirst()
				.map(ProductAxisEntity::getName)
				.orElse("수요축");
	}

	private ProductEntity product(long variantId) {
		VariantEntity variant = variants.findById(variantId)
				.orElseThrow(() -> new VariantNotFoundException(variantId));
		return products.findById(variant.getProductId())
				.orElseThrow(() -> new VariantNotFoundException(variantId));
	}
}
