package dev.hogumeter.core.domain.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * docs/03 3-2 관측시계 — staleness = (소스 마지막 성공 폴링) − lastEvidenceAt.
 * "무지를 부재로 오독 방지": 기준은 wall-clock이 아니라 마지막 성공 폴링 → 수집 공백 시 정지.
 */
class StalenessTest {

	@Test
	void stalenessIsPollMinusLastEvidence() {
		Instant lastPoll = Instant.parse("2026-07-15T00:00:00Z");
		Instant lastEvidence = Instant.parse("2026-07-10T00:00:00Z");

		assertThat(Staleness.of(lastPoll, lastEvidence)).isEqualTo(Duration.ofDays(5));
	}

	@Test
	void clockStopsWithCollectionGap() {
		// 수집 공백: lastPoll이 멈추면 벽시계가 흘러도 staleness는 lastPoll 기준으로 동결
		Instant lastEvidence = Instant.parse("2026-07-10T00:00:00Z");
		Instant stalledPoll = Instant.parse("2026-07-12T00:00:00Z"); // 이후 폴링 실패 → 이 값 고정

		assertThat(Staleness.of(stalledPoll, lastEvidence)).isEqualTo(Duration.ofDays(2)); // 벽시계 무관
	}
}
