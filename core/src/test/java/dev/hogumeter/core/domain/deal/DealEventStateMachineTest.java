package dev.hogumeter.core.domain.deal;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** BM-04 AC-6 상태기계 전이 전수 — 허용 전이 통과 + 비허용 전이 거부. */
class DealEventStateMachineTest {

	// 허용: NEW→ACTIVE, ACTIVE→VERIFIED, ACTIVE→ENDED, VERIFIED→ENDED. 나머지 전부 거부.
	@ParameterizedTest(name = "{0} → {1} : allowed={2}")
	@CsvSource({
			"NEW, ACTIVE, true", "NEW, VERIFIED, false", "NEW, ENDED, false", "NEW, NEW, false",
			"ACTIVE, VERIFIED, true", "ACTIVE, ENDED, true", "ACTIVE, NEW, false", "ACTIVE, ACTIVE, false",
			"VERIFIED, ENDED, true", "VERIFIED, NEW, false", "VERIFIED, ACTIVE, false", "VERIFIED, VERIFIED, false",
			"ENDED, NEW, false", "ENDED, ACTIVE, false", "ENDED, VERIFIED, false", "ENDED, ENDED, false"
	})
	void transitionAllowMatrix(DealStatus from, DealStatus to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
		if (allowed) {
			assertThat(from.transitionTo(to)).isEqualTo(to);
		} else {
			assertThatThrownBy(() -> from.transitionTo(to)).isInstanceOf(IllegalDealTransitionException.class);
		}
	}

	@Test
	void newActivatesToActive() {
		assertThat(aDealEvent().status(DealStatus.NEW).build().activate().status()).isEqualTo(DealStatus.ACTIVE);
	}

	@Test
	void activeVerifies() {
		assertThat(aDealEvent().status(DealStatus.ACTIVE).build().verify().status()).isEqualTo(DealStatus.VERIFIED);
	}

	@Test
	void activeAndVerifiedEnd() {
		assertThat(aDealEvent().status(DealStatus.ACTIVE).build().end().status()).isEqualTo(DealStatus.ENDED);
		assertThat(aDealEvent().status(DealStatus.VERIFIED).build().end().status()).isEqualTo(DealStatus.ENDED);
	}

	@Test
	void illegalTransitionsThrow() {
		assertThatThrownBy(() -> aDealEvent().status(DealStatus.NEW).build().verify())
				.isInstanceOf(IllegalDealTransitionException.class);
		assertThatThrownBy(() -> aDealEvent().status(DealStatus.VERIFIED).build().activate())
				.isInstanceOf(IllegalDealTransitionException.class);
		assertThatThrownBy(() -> aDealEvent().status(DealStatus.ENDED).build().end())
				.isInstanceOf(IllegalDealTransitionException.class);
	}

	@Test
	void priceChangeKeepsStatusAndUpdatesPriceExtremes() {
		DealEvent deal = aDealEvent().status(DealStatus.VERIFIED).firstSeen("2026-07-01T00:00:00Z")
				.withPriceFirst(900_000L).withPrices(900_000L, 900_000L, 900_000L).build();

		DealEvent changed = deal.recordPriceChange(850_000L, java.time.Instant.parse("2026-07-05T00:00:00Z"));

		assertThat(changed.status()).isEqualTo(DealStatus.VERIFIED); // 이벤트일 뿐, 상태 불변
		assertThat(changed.priceLast()).isEqualTo(850_000L);
		assertThat(changed.priceMin()).isEqualTo(850_000L);
		assertThat(changed.priceMax()).isEqualTo(900_000L);
		assertThat(changed.priceFirst()).isEqualTo(900_000L); // 대표가는 불변
		assertThat(changed.firstSeen()).isEqualTo(java.time.Instant.parse("2026-07-01T00:00:00Z")); // 발생시각 불변
		assertThat(changed.lastEvidenceAt()).isEqualTo(java.time.Instant.parse("2026-07-05T00:00:00Z")); // 적극 증거 전진
	}

	@Test
	void priceChangeOnEndedDealIsRejected() {
		assertThatThrownBy(() -> aDealEvent().status(DealStatus.ENDED).build()
				.recordPriceChange(800_000L, java.time.Instant.parse("2026-07-05T00:00:00Z")))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void firstSeenIsInvariantAcrossTransitions() {
		java.time.Instant occurred = java.time.Instant.parse("2026-06-01T00:00:00Z");
		DealEvent deal = aDealEvent().status(DealStatus.NEW).firstSeen(occurred).build();

		assertThat(deal.activate().firstSeen()).isEqualTo(occurred);
		assertThat(deal.activate().verify().firstSeen()).isEqualTo(occurred);
		assertThat(deal.activate().end().firstSeen()).isEqualTo(occurred);
		assertThat(deal.flagOutlier(OutlierFlag.LOWER).firstSeen()).isEqualTo(occurred);
		assertThat(deal.reject().firstSeen()).isEqualTo(occurred);
	}
}
