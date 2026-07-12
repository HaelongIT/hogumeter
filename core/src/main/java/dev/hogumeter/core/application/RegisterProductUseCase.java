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
		validate(cmd); // Q-49: 클라이언트 우회(curl) 방어 — 저장 전에 막아 500 대신 400을 낸다.

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

	/** 서버측 최소 불변(Q-49). 이름은 비어선 안 되고(NOT NULL·의미), variant가 없으면 값을 매길 대상이 없다. */
	private static void validate(RegisterProductCommand cmd) {
		if (cmd.name() == null || cmd.name().isBlank()) {
			throw new InvalidRegistrationException("제품명을 입력하세요");
		}
		if (cmd.variants() == null || cmd.variants().isEmpty()) {
			throw new InvalidRegistrationException("variant가 없습니다 — 가격축 값을 하나 이상 입력하세요");
		}
	}
}
