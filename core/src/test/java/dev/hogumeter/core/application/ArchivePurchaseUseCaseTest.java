package dev.hogumeter.core.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.IllegalPurchaseTransitionException;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUR-06 아카이브 — 수동 결말(docs/91 Q-62 잔여). {@code Purchase.archive()/reactivate()}는 순수
 * 도메인에 있었지만 프로덕션 호출자가 0이라 ARCHIVED에 영원히 닿을 길이 없었다 — Q-85가 배선한
 * "ARCHIVED면 🔥·목표가 억제"도 이 배선 없이는 평생 발화할 일이 없는 죽은 안전망이었다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ArchivePurchaseUseCaseTest {

	@Autowired
	ArchivePurchaseUseCase useCase;
	@Autowired
	PurchaseRepository purchases;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;

	private long purchaseIn(PurchaseState state) {
		long productId = products.save(new ProductEntity("아카이브 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		Purchase purchase = new Purchase(variantId, null, 900_000L, Instant.parse("2026-01-01T00:00:00Z"), 90, null,
				state);
		return purchases.save(new PurchaseEntity(purchase, Snapshot.unobserved("P=6mo,K=5"))).getId();
	}

	@Test
	void archivingAClosedPurchaseTransitionsToArchived() {
		long id = purchaseIn(PurchaseState.CLOSED);

		useCase.archive(id);

		assertThat(purchases.findById(id).orElseThrow().getState()).isEqualTo(PurchaseState.ARCHIVED);
	}

	@Test
	void archivingAnObservingPurchaseIsRejected() {
		long id = purchaseIn(PurchaseState.OBSERVING);

		assertThatThrownBy(() -> useCase.archive(id)).isInstanceOf(IllegalPurchaseTransitionException.class);
		assertThat(purchases.findById(id).orElseThrow().getState()).isEqualTo(PurchaseState.OBSERVING);
	}

	@Test
	void reactivatingAnArchivedPurchaseTransitionsToObserving() {
		long id = purchaseIn(PurchaseState.ARCHIVED);

		useCase.reactivate(id);

		assertThat(purchases.findById(id).orElseThrow().getState()).isEqualTo(PurchaseState.OBSERVING);
	}

	@Test
	void reactivatingANonArchivedPurchaseIsRejected() {
		long id = purchaseIn(PurchaseState.CLOSED);

		assertThatThrownBy(() -> useCase.reactivate(id)).isInstanceOf(IllegalPurchaseTransitionException.class);
	}

	@Test
	void unknownPurchaseIsRejected() {
		assertThatThrownBy(() -> useCase.archive(999_999_999L)).isInstanceOf(PurchaseNotFoundException.class);
	}
}
