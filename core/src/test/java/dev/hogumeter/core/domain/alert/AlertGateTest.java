package dev.hogumeter.core.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** AL-04 조용시간 보류 + 🔥 관통 + 경계(구간 시작 포함/끝 제외, wrap). */
class AlertGateTest {

	private final AlertGate gate = new AlertGate();

	private static Clock clockAtHour(int hour) {
		return Clock.fixed(Instant.parse(String.format("2026-07-05T%02d:00:00Z", hour)), ZoneOffset.UTC);
	}

	private static AlertDecision decision(AlertIntensity intensity) {
		return new AlertDecision(true, intensity, List.of(), List.of());
	}

	// wrap 23~8: 시작 23시 포함, 끝 8시 제외
	@ParameterizedTest(name = "hour={0} quiet={1}")
	@CsvSource({ "23, true", "0, true", "7, true", "8, false", "9, false", "22, false" })
	void quietHoursWrapAroundMidnight(int hour, boolean quiet) {
		assertThat(QuietHours.isQuiet(hour, 23, 8)).isEqualTo(quiet);
	}

	@ParameterizedTest(name = "hour={0} quiet={1}")
	@CsvSource({ "1, true", "5, true", "6, false", "0, false", "7, false" })
	void quietHoursSameDay(int hour, boolean quiet) {
		assertThat(QuietHours.isQuiet(hour, 1, 6)).isEqualTo(quiet);
	}

	@ParameterizedTest(name = "hour={0}")
	@CsvSource({ "0", "12", "23" })
	void noQuietHoursConfiguredIsNeverQuiet(int hour) {
		assertThat(QuietHours.isQuiet(hour, null, null)).isFalse();
	}

	@Test
	void nonJackpotDuringQuietHoursIsHeld() {
		AlertPolicy policy = new AlertPolicy(null, 23, 8);

		assertThat(gate.decide(decision(AlertIntensity.GOOD), policy, clockAtHour(2)))
				.isEqualTo(GateDecision.HOLD);
	}

	@Test
	void jackpotPiercesQuietHours() {
		AlertPolicy policy = new AlertPolicy(null, 23, 8);

		assertThat(gate.decide(decision(AlertIntensity.JACKPOT), policy, clockAtHour(2)))
				.isEqualTo(GateDecision.SEND_NOW); // 🔥 관통
	}

	@Test
	void nonJackpotOutsideQuietHoursSendsNow() {
		AlertPolicy policy = new AlertPolicy(null, 23, 8);

		assertThat(gate.decide(decision(AlertIntensity.GOOD), policy, clockAtHour(12)))
				.isEqualTo(GateDecision.SEND_NOW);
	}

	@Test
	void noQuietHoursSendsNow() {
		AlertPolicy policy = new AlertPolicy(null, null, null);

		assertThat(gate.decide(decision(AlertIntensity.GOOD), policy, clockAtHour(2)))
				.isEqualTo(GateDecision.SEND_NOW);
	}
}
