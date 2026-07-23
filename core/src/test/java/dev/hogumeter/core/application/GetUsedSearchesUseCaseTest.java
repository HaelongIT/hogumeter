package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.GetUsedSearchesUseCase.UsedSearchView;
import dev.hogumeter.core.application.RegisterUsedSearchCommand.BonusGroupCommand;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.used.BonusMode;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * 등록된 중고 검색 조회(읽기 전용). 등록 REST는 usedSearchId만 돌려주는데, web의 평가기·비교
 * 화면은 "이 제품에 어떤 검색이 등록돼 있나"를 다시 볼 방법이 필요하다(variant 조회와 같은 이유,
 * `GetProductsUseCase`의 거울상).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetUsedSearchesUseCaseTest {

	@Autowired
	GetUsedSearchesUseCase useCase;
	@Autowired
	RegisterUsedSearchUseCase register;
	@Autowired
	ProductRepository products;

	private long productId;

	@BeforeEach
	void setUp() {
		productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void listsRegisteredSearchesWithBonusGroups() {
		register.register(new RegisterUsedSearchCommand(productId, List.of("아이폰17", "256"),
				List.of(new BonusGroupCommand(List.of("미개봉", "새제품"), BonusMode.TRIGGER)),
				List.of("파손"), 800_000L, 15));

		List<UsedSearchView> found = useCase.listForProduct(productId);

		assertThat(found).hasSize(1);
		UsedSearchView view = found.get(0);
		assertThat(view.platform()).isEqualTo("BUNJANG");
		assertThat(view.required()).containsExactly("아이폰17", "256");
		assertThat(view.exclude()).containsExactly("파손");
		assertThat(view.targetPrice()).isEqualTo(800_000L);
		assertThat(view.pollIntervalMin()).isEqualTo(15);
		assertThat(view.bonusGroups()).hasSize(1);
		assertThat(view.bonusGroups().get(0).keywords()).containsExactly("미개봉", "새제품");
		assertThat(view.bonusGroups().get(0).mode()).isEqualTo(BonusMode.TRIGGER);
	}

	@Test
	void emptyForAProductWithNoSearches() {
		assertThat(useCase.listForProduct(productId)).isEmpty();
	}

	@Test
	void pollIntervalFloorAppliesEvenWhenNotRequested() {
		register.register(new RegisterUsedSearchCommand(productId, List.of("아이폰17"), List.of(), List.of(),
				null, null));

		assertThat(useCase.listForProduct(productId).get(0).pollIntervalMin())
				.isEqualTo(RegisterUsedSearchUseCase.POLL_INTERVAL_FLOOR_MIN);
	}
}
