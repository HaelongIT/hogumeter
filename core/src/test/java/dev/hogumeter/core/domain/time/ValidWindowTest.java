package dev.hogumeter.core.domain.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** docs/03 3-2 유효 창 = 대상 창(P) ∩ [observedFrom, now]. 관측 범위가 짧으면 그 범위로 잘린다. */
class ValidWindowTest {

	private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

	@Test
	void periodBindsWhenObservationIsOlder() {
		// observedFrom이 기간 시작보다 과거 → 기간(6개월=2026-01-15)이 하한
		ValidWindow w = ValidWindow.of(6, Instant.parse("2025-01-01T00:00:00Z"), NOW, ZoneOffset.UTC);

		assertThat(w.from()).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
		assertThat(w.to()).isEqualTo(NOW);
	}

	@Test
	void observationBindsWhenShorterThanPeriod() {
		// observedFrom이 기간 시작보다 최근 → 관측 시작이 하한("관측 범위 N개월")
		ValidWindow w = ValidWindow.of(6, Instant.parse("2026-04-01T00:00:00Z"), NOW, ZoneOffset.UTC);

		assertThat(w.from()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
		assertThat(w.observedMonths(ZoneOffset.UTC)).isEqualTo(3); // 4-15 ~ 7-15
	}

	@Test
	void containsRespectsBounds() {
		ValidWindow w = ValidWindow.of(6, Instant.parse("2026-04-01T00:00:00Z"), NOW, ZoneOffset.UTC);

		assertThat(w.contains(Instant.parse("2026-05-01T00:00:00Z"))).isTrue();
		assertThat(w.contains(Instant.parse("2026-03-01T00:00:00Z"))).isFalse(); // observedFrom 이전
		assertThat(w.contains(Instant.parse("2026-04-01T00:00:00Z"))).isTrue(); // 경계 포함
	}
}
