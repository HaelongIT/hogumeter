package dev.hogumeter.core.domain.time;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * docs/03 3-2 유효 창 — 모든 시간 통계는 대상 창(기간 P) ∩ [observedFrom, now]로 제한된다.
 * "부재=정보 vs 무지"를 구분: 관측 시작 전은 무지라 통계에서 잘라낸다. 창이 짧으면 "관측 범위 N개월" 표기.
 * CAD 주기·SIG 신선도·기준가 자동확장 상한(C-3)이 공용.
 */
public record ValidWindow(Instant from, Instant to) {

	public static ValidWindow of(int periodMonths, Instant observedFrom, Instant now, ZoneId zone) {
		Instant periodStart = ZonedDateTime.ofInstant(now, zone).minusMonths(periodMonths).toInstant();
		Instant from = periodStart.isBefore(observedFrom) ? observedFrom : periodStart;
		return new ValidWindow(from, now);
	}

	/** firstSeen 등 시각이 창 안(경계 포함)인지. */
	public boolean contains(Instant t) {
		return !t.isBefore(from) && !t.isAfter(to);
	}

	/** 실효 관측 범위(개월) — 창이 P보다 짧으면 그 값. "관측 범위 N개월" 표기용. */
	public long observedMonths(ZoneId zone) {
		return ChronoUnit.MONTHS.between(ZonedDateTime.ofInstant(from, zone), ZonedDateTime.ofInstant(to, zone));
	}
}
