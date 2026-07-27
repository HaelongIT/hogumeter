package dev.hogumeter.core.adapter.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.adapter.persistence.ProductEntity;
import dev.hogumeter.core.adapter.persistence.ProductRepository;
import dev.hogumeter.core.adapter.persistence.PurchaseEntity;
import dev.hogumeter.core.adapter.persistence.PurchaseRepository;
import dev.hogumeter.core.adapter.persistence.VariantEntity;
import dev.hogumeter.core.adapter.persistence.VariantRepository;
import dev.hogumeter.core.domain.product.DemandAxisMode;
import dev.hogumeter.core.domain.purchase.Purchase;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.Snapshot;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** PUR-06 아카이브 HTTP 경로 — 사람이 누르는 두 버튼이 실제로 저장까지 닿는지. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PurchaseControllerTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	PurchaseRepository purchases;
	@Autowired
	ProductRepository products;
	@Autowired
	VariantRepository variants;

	private long purchaseIn(PurchaseState state) {
		long productId = products.save(new ProductEntity("PUR 컨트롤러 테스트", "test", DemandAxisMode.GROUPED)).getId();
		long variantId = variants.save(new VariantEntity(productId, "256GB", Map.of())).getId();
		Purchase purchase = new Purchase(variantId, null, 900_000L, Instant.parse("2026-01-01T00:00:00Z"), 90, null,
				state);
		return purchases.save(new PurchaseEntity(purchase, Snapshot.unobserved("P=6mo,K=5"))).getId();
	}

	@Test
	void archivingAClosedPurchaseReturns204() throws Exception {
		long id = purchaseIn(PurchaseState.CLOSED);

		mockMvc.perform(post("/api/v1/purchases/" + id + "/archive")).andExpect(status().isNoContent());
	}

	@Test
	void archivingAnObservingPurchaseReturns409WithDomainCode() throws Exception {
		long id = purchaseIn(PurchaseState.OBSERVING);

		mockMvc.perform(post("/api/v1/purchases/" + id + "/archive"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PUR_ILLEGAL_TRANSITION"));
	}

	@Test
	void archivingAnUnknownPurchaseReturns404WithDomainCode() throws Exception {
		mockMvc.perform(post("/api/v1/purchases/999999999/archive"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
	}

	@Test
	void reactivatingAnArchivedPurchaseReturns204() throws Exception {
		long id = purchaseIn(PurchaseState.ARCHIVED);

		mockMvc.perform(post("/api/v1/purchases/" + id + "/reactivate")).andExpect(status().isNoContent());
	}
}
