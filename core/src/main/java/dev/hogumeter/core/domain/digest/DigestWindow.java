package dev.hogumeter.core.domain.digest;

import java.time.Instant;

/**
 * DIG-03 다이제스트 창(순수) — [max(직전 성공 발송, 활성 시각), 이번 발송) 반개구간.
 * "복귀는 신인": 재활성(활성 시각 &gt; 직전 발송) 시 기준점 무효화 → 활성 시각이 창 시작.
 * 플로우 귀속은 <b>가시화 시각</b>(occurrenceSet 자격 최초/재획득)이 창 안인지로 판정(늦확정·백필·재진입 커버).
 */
public record DigestWindow(Instant from, Instant to) {

	public static DigestWindow of(Instant priorSuccessfulSend, Instant activationTime, Instant thisSend) {
		Instant from = priorSuccessfulSend.isAfter(activationTime) ? priorSuccessfulSend : activationTime;
		return new DigestWindow(from, thisSend);
	}

	/** 반개구간 [from, to) — 시작 포함, 끝 제외. */
	public boolean contains(Instant visibilityTime) {
		return !visibilityTime.isBefore(from) && visibilityTime.isBefore(to);
	}

	/** firstSeen이 창 시작 이전이면 "발생 N일 전" 병기 대상(가시화는 창 안, 발생은 이전). */
	public boolean occurredBeforeWindow(Instant firstSeen) {
		return firstSeen.isBefore(from);
	}
}
