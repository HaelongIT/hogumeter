package dev.hogumeter.core.domain.signal;

import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.dealset.DealSets;
import dev.hogumeter.core.domain.time.Staleness;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * SIG 신호등 산출 — 순수 함수. 자격 = signalSet(비이상치·ENDED 아님) ∩ 신선도 자격(관측시계).
 * 색 = 활성 딜 최저 priceLast를 P25(🟢)·기준가(🟡)와 대조. SPARSE/NONE은 ⚪, m=0이면 🟢 불가.
 * <b>표시 전용 — 알림 트리거를 만들지 않는다</b>(B-5 비대칭). 신선도 상한·자격 한도는 주입(Q-24·Q-25).
 */
public class SignalCalculator {

	public SignalView compute(List<DealEvent> deals, BenchmarkView view, Instant lastSuccessfulPoll,
			Duration freshnessLimit, Duration qualifyLimit) {
		List<String> notes = new ArrayList<>();
		boolean goodDealLineEstablished = view.goodDealLine() != null; // m>0

		if (view.tier() == Tier.SPARSE || view.tier() == Tier.NONE) {
			return new SignalView(SignalColor.GRAY, goodDealLineEstablished, notes);
		}
		if (!goodDealLineEstablished) {
			notes.add("굿딜라인 미확립"); // m=0 → 🟢 불가
		}

		// signalSet ∩ 신선도 자격(자격 한도 내). 자격 상실(초과)은 신호에서 제외.
		List<DealEvent> qualified = DealSets.signalSet(deals).stream()
				.filter(d -> Staleness.of(lastSuccessfulPoll, d.lastEvidenceAt()).compareTo(qualifyLimit) <= 0)
				.toList();

		Optional<DealEvent> best = qualified.stream().min(Comparator.comparingLong(DealEvent::priceLast));
		if (best.isEmpty()) {
			return new SignalView(SignalColor.RED, goodDealLineEstablished, notes); // 딜 없음
		}
		long bestPrice = best.get().priceLast();
		if (Staleness.of(lastSuccessfulPoll, best.get().lastEvidenceAt()).compareTo(freshnessLimit) > 0) {
			notes.add("신선도 약화"); // 확신 상한 초과·자격 한도 이내
		}

		SignalColor color;
		if (goodDealLineEstablished && bestPrice <= view.goodDealLine()) {
			color = SignalColor.GREEN;
		} else if (view.benchmarkPrice() != null && bestPrice <= view.benchmarkPrice()) {
			color = SignalColor.YELLOW;
		} else {
			color = SignalColor.RED;
		}
		return new SignalView(color, goodDealLineEstablished, notes);
	}
}
