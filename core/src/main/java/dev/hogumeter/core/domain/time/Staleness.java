package dev.hogumeter.core.domain.time;

import java.time.Duration;
import java.time.Instant;

/**
 * docs/03 3-2 관측시계 staleness = (소스 사이트 마지막 성공 폴링) − lastEvidenceAt.
 * 기준을 wall-clock이 아닌 마지막 성공 폴링으로 두어, 수집 공백 시 시계가 정지한다(무지를 부재/노화로 오독 방지).
 */
public final class Staleness {

	private Staleness() {
	}

	public static Duration of(Instant lastSuccessfulPoll, Instant lastEvidenceAt) {
		return Duration.between(lastEvidenceAt, lastSuccessfulPoll);
	}
}
