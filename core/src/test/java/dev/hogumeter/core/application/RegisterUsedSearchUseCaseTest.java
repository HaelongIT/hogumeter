package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchBonusGroupRepository;
import dev.hogumeter.core.adapter.persistence.UsedSearchEntity;
import dev.hogumeter.core.adapter.persistence.UsedSearchRepository;
import dev.hogumeter.core.application.RegisterUsedSearchCommand.BonusGroupCommand;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.used.BonusMode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** USED-01 검색 등록 어댑터 — used_search + bonus_group 저장 계약(Testcontainers). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RegisterUsedSearchUseCaseTest {

	@Autowired
	RegisterUsedSearchUseCase useCase;
	@Autowired
	ProductRepository products;
	@Autowired
	UsedSearchRepository searches;
	@Autowired
	UsedSearchBonusGroupRepository bonusGroups;

	private long productId;

	@BeforeEach
	void setUp() {
		productId = products.save(new ProductEntity("아이폰 17", "스마트폰", DemandAxisMode.GROUPED)).getId();
	}

	@Test
	void registersSearchWithArraysAndBonusGroups() {
		long id = useCase.register(new RegisterUsedSearchCommand(productId,
				List.of("아이폰17", "256"),
				List.of(new BonusGroupCommand(List.of("S급", "민트"), BonusMode.SORT),
						new BonusGroupCommand(List.of("미개봉"), BonusMode.TRIGGER)),
				List.of("부품용", "침수"), 850_000L, 10));

		UsedSearchEntity saved = searches.findById(id).orElseThrow();
		assertThat(saved.getProductId()).isEqualTo(productId);
		assertThat(saved.getPlatform()).isEqualTo("BUNJANG");
		assertThat(saved.getRequiredKeywords()).containsExactly("아이폰17", "256");
		assertThat(saved.getExcludeKeywords()).containsExactly("부품용", "침수");
		assertThat(saved.getTargetPrice()).isEqualTo(850_000L);

		List<UsedSearchBonusGroupEntity> groups = bonusGroups.findByUsedSearchId(id);
		assertThat(groups).hasSize(2);
		assertThat(groups).anySatisfy(g -> {
			assertThat(g.getMode()).isEqualTo(BonusMode.SORT);
			assertThat(g.getKeywords()).containsExactly("S급", "민트");
		});
	}

	@Test
	void pollIntervalFlooredAtMarketplaceMinimum() {
		long id = useCase.register(new RegisterUsedSearchCommand(productId,
				List.of("아이폰17"), List.of(), List.of(), null, 1)); // 1분 요청 → 하한 10

		assertThat(searches.findById(id).orElseThrow().getPollIntervalMin()).isEqualTo(10);
	}

	@Test
	void nullPollIntervalDefaultsToFloor() {
		long id = useCase.register(new RegisterUsedSearchCommand(productId,
				List.of("아이폰17"), List.of(), List.of(), null, null));

		assertThat(searches.findById(id).orElseThrow().getPollIntervalMin()).isEqualTo(10);
	}
}
