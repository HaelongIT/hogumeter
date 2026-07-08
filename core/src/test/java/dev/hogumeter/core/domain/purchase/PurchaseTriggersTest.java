package dev.hogumeter.core.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PUR-03 상태×트리거 표. 🔥·목표가는 ARCHIVED만 off / paidPrice 하회는 OBSERVING만·"&lt;"경계·복수관찰 OR /
 * 상대평가는 CLOSED만. 여기선 매트릭스 enablement + paidPrice 발화를 검증(상대평가 발화는 후속).
 */
class PurchaseTriggersTest {

	private static final Instant T = Instant.parse("2026-04-01T00:00:00Z");

	private static Purchase observingAt(long paidPrice) {
		return Purchase.observing(1L, "256GB", paidPrice, T, 90);
	}

	@Test
	void enablementMatrixPerState() {
		assertThat(PurchaseTriggers.enabledFor(PurchaseState.OBSERVING))
				.containsExactlyInAnyOrder(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET, PurchaseTrigger.PAID_PRICE);
		assertThat(PurchaseTriggers.enabledFor(PurchaseState.REPORT_PENDING))
				.containsExactlyInAnyOrder(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET);
		assertThat(PurchaseTriggers.enabledFor(PurchaseState.CLOSED))
				.containsExactlyInAnyOrder(PurchaseTrigger.JACKPOT, PurchaseTrigger.TARGET, PurchaseTrigger.RELATIVE);
		assertThat(PurchaseTriggers.enabledFor(PurchaseState.ARCHIVED)).isEmpty(); // 전부 off
	}

	// paidPrice 하회 = "<" 경계(같으면 미발화)
	@ParameterizedTest(name = "deal={0} vs paid 890,000 → fire={1}")
	@CsvSource({ "889999, true", "890000, false", "900000, false" })
	void paidPriceStrictlyBelowBoundary(long dealPrice, boolean fires) {
		assertThat(PurchaseTriggers.paidPriceTriggerFires(dealPrice, List.of(observingAt(890_000L))))
				.isEqualTo(fires);
	}

	@Test
	void multipleObservationsUseOr() {
		// 관찰 2건(890k, 850k). deal 860k → 890k보다 미만이라 발화(850k보다 크더라도 OR)
		List<Purchase> purchases = List.of(observingAt(890_000L), observingAt(850_000L));
		assertThat(PurchaseTriggers.paidPriceTriggerFires(860_000L, purchases)).isTrue();
	}

	@Test
	void paidPriceIgnoresNonObservingPurchases() {
		// CLOSED 관찰의 paidPrice는 paidPrice 트리거에서 무시(OBSERVING만)
		Purchase closed = observingAt(900_000L).expire().close();
		assertThat(PurchaseTriggers.paidPriceTriggerFires(880_000L, List.of(closed))).isFalse();
	}
}
