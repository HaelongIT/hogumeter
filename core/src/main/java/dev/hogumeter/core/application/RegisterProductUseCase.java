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
		if (cmd.aliases() != null) { // 별칭은 선택 — null은 "없음"으로 다룬다(500 대신 정상 저장, 매칭 힌트만 준다)
			for (String alias : cmd.aliases()) {
				aliases.save(new AliasEntity(productId, alias));
			}
		}
		return productId;
	}

	/**
	 * 서버측 최소 불변(Q-49). 클라이언트 검증(`buildCommand`)은 편의일 뿐 — curl로 직접 치면 통과하므로
	 * 저장 전에 막아 <b>500 대신 400</b>을 낸다. 빈 이름은 DB NOT NULL을, null 컬렉션·모드는 NPE·NOT NULL을
	 * 뚫어 500이 됐다. <b>빈 목록은 허용</b>(기존 계약)하되 <b>null은 거절</b>한다 — 축·모드는 구조상 필수다.
	 */
	private static void validate(RegisterProductCommand cmd) {
		if (cmd.name() == null || cmd.name().isBlank()) {
			throw new InvalidRegistrationException("제품명을 입력하세요");
		}
		if (cmd.demandAxisMode() == null) {
			// DB DEFAULT('GROUPED')는 컬럼 생략 시만 적용되고 Hibernate는 null을 명시 삽입한다. 기본값을
			// 코드로 복제하면 사본이 드리프트하므로(정본=DB) 지어내지 않고 거절한다 — 모드는 지정해야 한다.
			throw new InvalidRegistrationException("수요축 모드(GROUPED/SPLIT)를 지정하세요");
		}
		if (cmd.axes() == null) {
			throw new InvalidRegistrationException("축이 없습니다 — variant를 정의할 가격축이 필요합니다");
		}
		if (cmd.variants() == null || cmd.variants().isEmpty()) {
			throw new InvalidRegistrationException("variant가 없습니다 — 가격축 값을 하나 이상 입력하세요");
		}
	}
}
