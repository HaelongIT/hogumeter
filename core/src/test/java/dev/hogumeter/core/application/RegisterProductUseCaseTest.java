package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.AliasEntity;
import dev.hogumeter.core.adapter.persistence.AliasRepository;
import dev.hogumeter.core.adapter.persistence.ProductAxisRepository;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.AxisType;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** 슬라이스 1 등록 배선 — 제품+축+variant+별칭 저장(실 스키마 V1 + Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegisterProductUseCaseTest {

	@Autowired
	ProductRepository productRepo;
	@Autowired
	ProductAxisRepository axisRepo;
	@Autowired
	VariantRepository variantRepo;
	@Autowired
	AliasRepository aliasRepo;

	private RegisterProductUseCase useCase;

	@BeforeEach
	void setUp() {
		aliasRepo.deleteAll();
		variantRepo.deleteAll();
		axisRepo.deleteAll();
		productRepo.deleteAll();
		useCase = new RegisterProductUseCase(productRepo, axisRepo, variantRepo, aliasRepo);
	}

	@Test
	void registersProductWithAxesVariantsAndAliases() {
		RegisterProductCommand cmd = new RegisterProductCommand(
				"아이폰 17", "스마트폰", DemandAxisMode.SPLIT,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB", "512GB")),
						new RegisterProductCommand.Axis(AxisType.DEMAND, "색상", List.of("블랙", "화이트"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB")),
						new RegisterProductCommand.Variant("512GB", Map.of("용량", "512GB"))),
				List.of("아이폰17", "iPhone17"));

		long productId = useCase.register(cmd);

		var product = productRepo.findById(productId).orElseThrow();
		assertThat(product.getName()).isEqualTo("아이폰 17");
		assertThat(product.getDemandAxisMode()).isEqualTo(DemandAxisMode.SPLIT);

		assertThat(axisRepo.findByProductId(productId)).hasSize(2);

		List<VariantEntity> variants = variantRepo.findByProductId(productId);
		assertThat(variants).extracting(VariantEntity::getLabel).containsExactlyInAnyOrder("256GB", "512GB");
		VariantEntity v256 = variants.stream().filter(v -> v.getLabel().equals("256GB")).findFirst().orElseThrow();
		assertThat(v256.getPriceAxisValues()).containsEntry("용량", "256GB");

		assertThat(aliasRepo.findByProductId(productId)).extracting(AliasEntity::getAlias)
				.containsExactlyInAnyOrder("아이폰17", "iPhone17");
	}

	// Q-49: 서버측 검증 — curl로 빈 이름을 치면 500(DB NOT NULL) 대신 막고 아무것도 저장하지 않는다.
	@Test
	void blankNameIsRejectedAndNothingSaved() {
		RegisterProductCommand cmd = new RegisterProductCommand("   ", "폰", DemandAxisMode.GROUPED,
				List.of(), List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))), List.of());

		assertThatThrownBy(() -> useCase.register(cmd)).isInstanceOf(InvalidRegistrationException.class);
		assertThat(productRepo.findAll()).isEmpty();
	}

	@Test
	void emptyVariantsIsRejected() {
		RegisterProductCommand cmd = new RegisterProductCommand("아이폰 17", "폰", DemandAxisMode.GROUPED,
				List.of(), List.of(), List.of());

		assertThatThrownBy(() -> useCase.register(cmd)).isInstanceOf(InvalidRegistrationException.class);
	}

	/**
	 * Q-49 잔여: 빈 이름·빈 variant는 막았으나 <b>null 필드</b>는 여전히 NPE → 500이었다. `axes`가 null이면
	 * variant를 정의할 축이 없다 — variant null과 같은 부류라 400으로 막는다(빈 목록은 기존대로 허용).
	 */
	@Test
	void nullAxesIsRejected() {
		RegisterProductCommand cmd = new RegisterProductCommand("아이폰 17", "폰", DemandAxisMode.GROUPED,
				null, List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))), List.of());

		assertThatThrownBy(() -> useCase.register(cmd)).isInstanceOf(InvalidRegistrationException.class);
		assertThat(productRepo.findAll()).isEmpty();
	}

	/**
	 * Q-49 잔여: `demand_axis_mode`는 NOT NULL이라 null 모드는 DB 제약 위반 → 500이었다. DB DEFAULT('GROUPED')는
	 * 컬럼을 <b>생략</b>할 때만 적용되는데 Hibernate는 null을 명시 삽입한다. 기본값을 코드로 복제하면 사본이
	 * 드리프트하므로(정본=DB) 지어내지 않고 <b>400으로 거절</b>한다 — 모드는 클라이언트가 지정해야 한다.
	 */
	@Test
	void nullDemandAxisModeIsRejected() {
		RegisterProductCommand cmd = new RegisterProductCommand("아이폰 17", "폰", null,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))), List.of());

		assertThatThrownBy(() -> useCase.register(cmd)).isInstanceOf(InvalidRegistrationException.class);
		assertThat(productRepo.findAll()).isEmpty();
	}

	/** 별칭은 선택이다(매칭 사전 시드) — null은 "없음"으로 다뤄 500이 아니라 정상 저장한다. 없으면 매칭 힌트만 준다. */
	@Test
	void nullAliasesIsTreatedAsEmptyAndProductSaves() {
		RegisterProductCommand cmd = new RegisterProductCommand("아이폰 17", "폰", DemandAxisMode.GROUPED,
				List.of(new RegisterProductCommand.Axis(AxisType.PRICE, "용량", List.of("256GB"))),
				List.of(new RegisterProductCommand.Variant("256GB", Map.of("용량", "256GB"))), null);

		long productId = useCase.register(cmd);

		assertThat(aliasRepo.findByProductId(productId)).isEmpty();
	}
}
