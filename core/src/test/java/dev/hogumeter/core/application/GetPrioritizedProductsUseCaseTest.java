package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.application.GetPrioritizedProductsUseCase.PrioritizedProduct;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * PRI ②축소(docs/19) 정렬 목록 배선 — 대기 판정({@code PriorityQueueTest}가 이미 순수하게 잠갔다) 자체가
 * 아니라, 그 판정에 필요한 재료(Purchase 존재 여부)가 실제로 모이고 정렬이 계약대로 되는지를 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class GetPrioritizedProductsUseCaseTest {

	@Autowired
	GetPrioritizedProductsUseCase getPrioritized;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;
	@Autowired
	PurchaseRepository purchases;
	@Autowired
	SetProductArchivedUseCase setArchived;

	private long product(String name) {
		return products.save(new ProductEntity(name, "test", DemandAxisMode.GROUPED)).getId();
	}

	private List<PrioritizedProduct> mine(long... ids) {
		List<Long> wanted = java.util.Arrays.stream(ids).boxed().toList();
		return getPrioritized.list().stream().filter(p -> wanted.contains(p.productId())).toList();
	}

	@Test
	void aFreshProductWithNoPurchaseIsWaiting() {
		long id = product("정렬A");

		PrioritizedProduct item = mine(id).get(0);

		assertThat(item.waiting()).isTrue();
		assertThat(item.manuallyCompleted()).isFalse();
	}

	@Test
	void aProductWithAnyPurchaseIsNotWaiting() {
		long productId = product("정렬B");
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, null, 900_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		PrioritizedProduct item = mine(productId).get(0);

		assertThat(item.waiting()).isFalse();
	}

	@Test
	void waitingProductsSortBeforeNonWaitingOnesRegardlessOfRank() {
		// 비대기를 먼저 만들어 id가 더 작게 한다 — "id 오름차순"이라는 우연한 폴백으로 통과하지 않게(뮤테이션 확인).
		long nonWaiting = product("정렬C-비대기");
		long waitingUnranked = product("정렬C-대기-미지정");
		long variantId = variants.save(new VariantEntity(nonWaiting, "256GB", Map.of())).getId();
		purchases.save(new PurchaseEntity(
				Purchase.observing(variantId, null, 900_000L, Instant.parse("2026-06-01T00:00:00Z"), 90),
				Snapshot.unobserved("P=6mo,K=5")));

		List<PrioritizedProduct> ordered = mine(waitingUnranked, nonWaiting);

		assertThat(ordered).extracting(PrioritizedProduct::productId)
				.containsExactly(waitingUnranked, nonWaiting);
	}

	/** Q-91: 보관된 제품은 우선순위 목록에도 안 낸다(대기 여부와 무관하게 숨김). */
	@Test
	void archivedProductsAreExcludedFromTheList() {
		long id = product("정렬E-보관됨");
		setArchived.archive(id);

		assertThat(mine(id)).isEmpty();
	}

	@Test
	void rankedWaitingProductsSortByRankBeforeUnrankedOnes() {
		long unranked = product("정렬D-미지정");
		long ranked = product("정렬D-순번2");
		products.findById(ranked).orElseThrow().setPriorityRank(2);
		products.save(products.findById(ranked).orElseThrow());

		List<PrioritizedProduct> ordered = mine(unranked, ranked);

		assertThat(ordered).extracting(PrioritizedProduct::productId).containsExactly(ranked, unranked);
	}
}
