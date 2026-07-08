package dev.hogumeter.core.domain.cadence;

import dev.hogumeter.core.domain.Quantiles;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import dev.hogumeter.core.domain.time.ValidWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * CAD 딜 주기 산출 — 순수 함수(Clock 주입). 집합 = occurrenceSet, 축 = firstSeen.
 * eventCount·간격 median은 창(P ∩ [observedFrom, now])에서, 경과일은 P 무관·관측 범위 내 최신 발생 기준.
 * "다음 딜 예상일"은 산출하지 않는다(예측 금지). 자동확장 없음.
 */
public class CadenceCalculator {

	public CadenceView compute(List<DealEvent> deals, Instant observedFrom, int periodMonths, int kDisplay,
			Clock clock) {
		Instant now = clock.instant();
		ZoneId zone = clock.getZone();
		List<DealEvent> occurrence = DealSets.occurrenceSet(deals);

		ValidWindow window = ValidWindow.of(periodMonths, observedFrom, now, zone);
		List<Instant> inWindow = occurrence.stream()
				.map(DealEvent::firstSeen)
				.filter(window::contains)
				.sorted()
				.toList();
		int eventCount = inWindow.size();
		boolean guardMet = eventCount >= kDisplay;

		Long intervalMedianDays = null;
		if (guardMet && eventCount >= 2) {
			List<Long> intervals = new ArrayList<>();
			for (int i = 1; i < inWindow.size(); i++) {
				intervals.add(Duration.between(inWindow.get(i - 1), inWindow.get(i)).toDays());
			}
			intervalMedianDays = Quantiles.medianWon(intervals);
		}

		// 경과일: P 무관 — 관측 범위 [observedFrom, now] 내 최신 발생에서 조회 시점까지
		Optional<Instant> latest = occurrence.stream()
				.map(DealEvent::firstSeen)
				.filter(t -> !t.isBefore(observedFrom) && !t.isAfter(now))
				.max(Comparator.naturalOrder());
		Long elapsedDays = latest.map(t -> Duration.between(t, now).toDays()).orElse(null);

		return new CadenceView(eventCount, intervalMedianDays, elapsedDays,
				(int) window.observedMonths(zone), guardMet);
	}
}
