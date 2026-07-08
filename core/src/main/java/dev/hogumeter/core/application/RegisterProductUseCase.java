package dev.hogumeter.core.application;

import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.ProductAxisEntity;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 제품 등록 유스케이스 — 제품·축·variant·별칭을 한 트랜잭션으로 생성하고 productId를 반환한다. */
@Service
public class RegisterProductUseCase {

	private final ProductRepository products;
	private final ProductAxisRepository axes;
	private final VariantRepository variants;
	private final AliasRepository aliases;

	public RegisterProductUseCase(ProductRepository products, ProductAxisRepository axes,
			VariantRepository variants, AliasRepository aliases) {
		this.products = products;
		this.axes = axes;
		this.variants = variants;
		this.aliases = aliases;
	}

	@Transactional
	public long register(RegisterProductCommand cmd) {
		ProductEntity product = products.save(
				new ProductEntity(cmd.name(), cmd.category(), cmd.demandAxisMode()));
		long productId = product.getId();

		for (RegisterProductCommand.Axis axis : cmd.axes()) {
			axes.save(new ProductAxisEntity(productId, axis.axisType(), axis.name(), axis.allowedValues()));
		}
		for (RegisterProductCommand.Variant variant : cmd.variants()) {
			variants.save(new VariantEntity(productId, variant.label(), variant.priceAxisValues()));
		}
		for (String alias : cmd.aliases()) {
			aliases.save(new AliasEntity(productId, alias));
		}
		return productId;
	}
}
