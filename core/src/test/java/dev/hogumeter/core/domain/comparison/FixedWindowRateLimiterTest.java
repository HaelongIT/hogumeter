package dev.hogumeter.core.domain.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * CMP-02 SEC-04 — extension ingest 레이트리밋(순수, 시각 주입). 확장 1개·사용자 1인 규모라 전역 카운터
 * 하나로 충분하다(PERF 과최적화 금지). 창(window) 경과는 벽시계가 아니라 <b>주입된 now</b>로 판정한다.
 */
class FixedWindowRateLimiterTest {

	private static final Instant T0 = Instant.parse("2026-07-23T00:00:00Z");

	@Test
	void allowsUpToTheLimitWithinAWindow() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3);

		assertThat(limiter.tryAcquire(T0)).isTrue();
		assertThat(limiter.tryAcquire(T0.plusSeconds(1))).isTrue();
		assertThat(limiter.tryAcquire(T0.plusSeconds(2))).isTrue();
		assertThat(limiter.tryAcquire(T0.plusSeconds(3))).isFalse(); // 4번째는 같은 창 안에서 거절
	}

	@Test
	void resetsAfterTheWindowElapses() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1);

		assertThat(limiter.tryAcquire(T0)).isTrue();
		assertThat(limiter.tryAcquire(T0.plus(Duration.ofSeconds(30)))).isFalse(); // 아직 같은 창

		assertThat(limiter.tryAcquire(T0.plus(Duration.ofMinutes(1)))).isTrue(); // 창이 넘어갔다
	}

	@Test
	void limitOfZeroAlwaysRejects() {
		FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(0);

		assertThat(limiter.tryAcquire(T0)).isFalse();
	}
}
