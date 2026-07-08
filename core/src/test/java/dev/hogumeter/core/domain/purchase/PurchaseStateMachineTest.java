package dev.hogumeter.core.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PUR-01 Purchase 상태기계 — OBSERVING→REPORT_PENDING→CLOSED→ARCHIVED(+재활성). 복수 관찰 공존. */
class PurchaseStateMachineTest {

	private static final Instant PURCHASED = Instant.parse("2026-04-01T00:00:00Z");

	private static Purchase observing() {
		return Purchase.observing(1L, "256GB", 890_000L, PURCHASED, 90);
	}

	@Test
	void lifecycleTransitions() {
		Purchase p = observing();
		assertThat(p.state()).isEqualTo(PurchaseState.OBSERVING);
		assertThat(p.expire().state()).isEqualTo(PurchaseState.REPORT_PENDING);
		assertThat(p.expire().close().state()).isEqualTo(PurchaseState.CLOSED);
		assertThat(p.expire().close().archive().state()).isEqualTo(PurchaseState.ARCHIVED);
		assertThat(p.expire().close().archive().reactivate().state()).isEqualTo(PurchaseState.OBSERVING);
	}

	// 허용: OBSERVING→REPORT_PENDING, REPORT_PENDING→CLOSED, CLOSED→ARCHIVED, ARCHIVED→OBSERVING. 나머지 거부.
	@ParameterizedTest(name = "{0} → {1} : allowed={2}")
	@CsvSource({
			"OBSERVING, REPORT_PENDING, true", "OBSERVING, CLOSED, false", "OBSERVING, ARCHIVED, false",
			"OBSERVING, OBSERVING, false",
			"REPORT_PENDING, CLOSED, true", "REPORT_PENDING, OBSERVING, false", "REPORT_PENDING, ARCHIVED, false",
			"CLOSED, ARCHIVED, true", "CLOSED, OBSERVING, false", "CLOSED, REPORT_PENDING, false",
			"ARCHIVED, OBSERVING, true", "ARCHIVED, CLOSED, false", "ARCHIVED, REPORT_PENDING, false"
	})
	void transitionMatrix(PurchaseState from, PurchaseState to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
	}

	@Test
	void illegalTransitionsThrow() {
		assertThatThrownBy(() -> observing().close()).isInstanceOf(IllegalPurchaseTransitionException.class);
		assertThatThrownBy(() -> observing().archive()).isInstanceOf(IllegalPurchaseTransitionException.class);
		assertThatThrownBy(() -> observing().reactivate()).isInstanceOf(IllegalPurchaseTransitionException.class);
	}

	@Test
	void observationEndsAfterObservationDays() {
		Purchase p = observing(); // 90일 관찰
		Instant ends = PURCHASED.plus(Duration.ofDays(90));

		assertThat(p.observationEndsAt()).isEqualTo(ends);
		assertThat(p.isExpired(ends)).isTrue(); // 만료 시점 정각 = 만료
		assertThat(p.isExpired(ends.minus(Duration.ofDays(1)))).isFalse();
	}

	@Test
	void multipleObservationsCoexistPerVariant() {
		Purchase a = Purchase.observing(1L, "256GB", 890_000L, PURCHASED, 90);
		Purchase b = Purchase.observing(1L, "256GB", 850_000L, PURCHASED, 90);
		assertThat(a).isNotEqualTo(b); // variant 점유 없음 — 독립 공존
	}
}
