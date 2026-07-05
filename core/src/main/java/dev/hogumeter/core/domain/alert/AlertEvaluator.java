package dev.hogumeter.core.domain.alert;

import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * AL-02 알림 트리거 판정(순수 도메인). 딜 가격을 기준가/P25/목표가/현재가와 대조해 충족 조건을 모으고,
 * 최고 강도 1발 + 나머지 병기 + 딱지 라벨을 담은 {@link AlertDecision}을 낸다. 발송·시간은 여기서 다루지 않는다.
 */
public class AlertEvaluator {

	public AlertDecision evaluate(DealEvent deal, BenchmarkView view, AlertPolicy policy, BenchmarkParams params) {
		long price = deal.priceFirst();
		EnumSet<AlertIntensity> satisfied = EnumSet.noneOf(AlertIntensity.class);

		if (deal.outlierFlag() == OutlierFlag.LOWER) {
			satisfied.add(AlertIntensity.JACKPOT); // 🔥 최우선
		}
		if (policy.targetPrice() != null && price <= policy.targetPrice()) {
			satisfied.add(AlertIntensity.TARGET);
		}
		switch (view.tier()) {
			case SUFFICIENT -> {
				if (view.goodDealLine() != null && price <= view.goodDealLine()) {
					satisfied.add(AlertIntensity.SPECIAL);
				}
				if (view.benchmarkPrice() != null && price <= view.benchmarkPrice()) {
					satisfied.add(AlertIntensity.GOOD);
				}
			}
			case SPARSE -> {
				Long yardstick = lowestCasePrice(view); // 통계 대신 보유 최저가 잣대
				if (yardstick != null && price <= yardstick) {
					satisfied.add(AlertIntensity.GOOD);
				}
			}
			case NONE -> {
				if (qualifiesColdStart(price, view.currentPrice(), params)) {
					satisfied.add(AlertIntensity.GOOD); // 기준 미확립 폴백
				}
			}
		}

		List<String> labels = labels(deal, view);
		if (satisfied.isEmpty()) {
			return new AlertDecision(false, AlertIntensity.NONE, List.of(), labels);
		}
		List<AlertIntensity> ordered = satisfied.stream().sorted().toList(); // 선언 순서 = 우선순위
		return new AlertDecision(true, ordered.get(0), ordered.subList(1, ordered.size()), labels);
	}

	private static Long lowestCasePrice(BenchmarkView view) {
		return view.cases().stream()
				.mapToLong(BenchmarkView.DealRef::price)
				.min()
				.stream().boxed()
				.findFirst()
				.orElse(null);
	}

	private static boolean qualifiesColdStart(long price, long currentPrice, BenchmarkParams params) {
		BigDecimal threshold = BigDecimal.valueOf(currentPrice)
				.multiply(BigDecimal.ONE.subtract(params.coldStartJackpotRatio()));
		return BigDecimal.valueOf(price).compareTo(threshold) <= 0;
	}

	private static List<String> labels(DealEvent deal, BenchmarkView view) {
		List<String> labels = new ArrayList<>();
		labels.add(deal.crossVerified() ? deal.sourceSites().size() + "개 사이트 검증" : "미검증(단독)");
		if (view.tier() == Tier.SPARSE) {
			labels.add("표본 " + view.n() + "건 · 참고용");
		}
		if (view.tier() == Tier.NONE) {
			labels.add("기준 미확립 · 참고용");
		}
		return labels;
	}
}
