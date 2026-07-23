package dev.hogumeter.core.domain.comparison;

import java.time.Duration;
import java.time.Instant;

/**
 * CMP-02 SEC-04 — 고정 1분 창 레이트리밋. 확장 1개·사용자 1인 규모라 전역 카운터 하나로 충분하다.
 * 시각은 호출자가 준다({@code now()} 직접 호출 금지, 테스트 결정성).
 */
public class FixedWindowRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private final int limitPerWindow;
	private Instant windowStart;
	private int count;

	public FixedWindowRateLimiter(int limitPerWindow) {
		this.limitPerWindow = limitPerWindow;
	}

	public synchronized boolean tryAcquire(Instant now) {
		if (windowStart == null || Duration.between(windowStart, now).compareTo(WINDOW) >= 0) {
			windowStart = now;
			count = 0;
		}
		if (count >= limitPerWindow) {
			return false;
		}
		count++;
		return true;
	}
}
