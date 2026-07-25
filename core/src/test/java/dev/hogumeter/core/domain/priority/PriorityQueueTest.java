package dev.hogumeter.core.domain.priority;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PRI ②축소(docs/19) 대기 판정 순수 계약. */
class PriorityQueueTest {

	@Test
	void aProductWithNoPurchaseAndNotManuallyCompletedIsWaiting() {
		assertThat(PriorityQueue.isWaiting(false, false)).isTrue();
	}

	@Test
	void aProductWithAnyPurchaseIsNotWaiting() {
		assertThat(PriorityQueue.isWaiting(true, false)).isFalse();
	}

	@Test
	void aManuallyCompletedProductIsNotWaitingEvenWithoutAPurchase() {
		assertThat(PriorityQueue.isWaiting(false, true)).isFalse();
	}

	@Test
	void aProductWithBothConditionsIsNotWaiting() {
		assertThat(PriorityQueue.isWaiting(true, true)).isFalse();
	}
}
