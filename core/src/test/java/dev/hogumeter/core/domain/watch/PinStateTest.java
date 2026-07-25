package dev.hogumeter.core.domain.watch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** WATCH(docs/17) 핀 결말 상태기계 — ACTIVE에서만 나가고, 결말(BOUGHT·MISSED·DROPPED)은 종착이다. */
class PinStateTest {

	@ParameterizedTest(name = "{0} → {1} : allowed={2}")
	@CsvSource({
			"ACTIVE, BOUGHT, true", "ACTIVE, MISSED, true", "ACTIVE, DROPPED, true", "ACTIVE, ACTIVE, false",
			"BOUGHT, ACTIVE, false", "BOUGHT, MISSED, false", "BOUGHT, DROPPED, false", "BOUGHT, BOUGHT, false",
			"MISSED, ACTIVE, false", "MISSED, BOUGHT, false", "MISSED, DROPPED, false", "MISSED, MISSED, false",
			"DROPPED, ACTIVE, false", "DROPPED, BOUGHT, false", "DROPPED, MISSED, false", "DROPPED, DROPPED, false"
	})
	void transitionMatrix(PinState from, PinState to, boolean allowed) {
		assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
		if (allowed) {
			assertThat(from.transitionTo(to)).isEqualTo(to);
		}
		else {
			assertThatThrownBy(() -> from.transitionTo(to)).isInstanceOf(IllegalPinTransitionException.class);
		}
	}

	@Test
	void terminalStatesAreNotActive() {
		assertThat(PinState.BOUGHT.isTerminal()).isTrue();
		assertThat(PinState.MISSED.isTerminal()).isTrue();
		assertThat(PinState.DROPPED.isTerminal()).isTrue();
		assertThat(PinState.ACTIVE.isTerminal()).isFalse();
	}
}
