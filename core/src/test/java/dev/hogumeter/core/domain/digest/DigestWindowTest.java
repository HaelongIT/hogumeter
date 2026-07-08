package dev.hogumeter.core.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * DIG-03 다이제스트 창 = [max(직전 성공 발송, 활성 시각), 이번 발송) 반개구간. "복귀는 신인"으로 기준점 무효화.
 * 플로우 귀속 = 가시화 시각. firstSeen이 창 시작 이전이면 "발생 N일 전" 병기(가시화는 창 안).
 */
class DigestWindowTest {

	private static final Instant THIS_SEND = Instant.parse("2026-07-05T20:00:00Z");

	@Test
	void halfOpenIntervalExcludesEnd() {
		DigestWindow w = DigestWindow.of(Instant.parse("2026-06-28T20:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"), THIS_SEND);

		assertThat(w.contains(w.from())).isTrue(); // 시작 포함
		assertThat(w.contains(Instant.parse("2026-07-01T00:00:00Z"))).isTrue();
		assertThat(w.contains(THIS_SEND)).isFalse(); // 끝 제외(반개구간)
	}

	@Test
	void reactivationResetsReferenceNewbie() {
		// 활성 시각이 직전 발송보다 최근(재활성) → 창 시작 = 활성 시각("복귀는 신인")
		DigestWindow w = DigestWindow.of(Instant.parse("2026-06-01T00:00:00Z"),
				Instant.parse("2026-06-20T00:00:00Z"), THIS_SEND);

		assertThat(w.from()).isEqualTo(Instant.parse("2026-06-20T00:00:00Z"));
	}

	@Test
	void priorSendBindsWhenNoRecentReactivation() {
		DigestWindow w = DigestWindow.of(Instant.parse("2026-06-28T20:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"), THIS_SEND);

		assertThat(w.from()).isEqualTo(Instant.parse("2026-06-28T20:00:00Z"));
	}

	@Test
	void occurredBeforeWindowFlagsBackfillOrLateConfirm() {
		DigestWindow w = DigestWindow.of(Instant.parse("2026-06-28T20:00:00Z"),
				Instant.parse("2026-01-01T00:00:00Z"), THIS_SEND);

		assertThat(w.occurredBeforeWindow(Instant.parse("2026-06-01T00:00:00Z"))).isTrue(); // 창 이전 발생 → 병기
		assertThat(w.occurredBeforeWindow(Instant.parse("2026-07-01T00:00:00Z"))).isFalse();
	}
}
