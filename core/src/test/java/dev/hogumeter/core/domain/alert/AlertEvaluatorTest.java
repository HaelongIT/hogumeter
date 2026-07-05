package dev.hogumeter.core.domain.alert;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.DealRef;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.Gap;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.PricePoint;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AL-02 알림 트리거 매트릭스 + 최고강도 1발 원칙(🔥 > 특가 > 목표가 > 괜찮은 딜).
 * 발송 어댑터 없이 "무엇을 보내기로 했는가"(AlertDecision)만 검증.
 */
class AlertEvaluatorTest {

	private final AlertEvaluator evaluator = new AlertEvaluator();
	private final BenchmarkParams params = BenchmarkParams.defaults(); // coldstart 30%

	// SUFFICIENT: benchmark 890k, P25(goodDeal) 850k, 최저 820k, 현재가 990k
	private static BenchmarkView sufficient() {
		return new BenchmarkView(Tier.SUFFICIENT, 890_000L, 850_000L,
				new PricePoint(820_000L, LocalDate.of(2026, 6, 1)), null,
				7, 3, null, 990_000L, new Gap(null, null), List.of());
	}

	private static BenchmarkView sparse(long current, long... casePrices) {
		List<DealRef> cases = Arrays.stream(casePrices).boxed()
				.map(p -> new DealRef(p, LocalDate.of(2026, 6, 1), "ppomppu", "u")).toList();
		return new BenchmarkView(Tier.SPARSE, null, null, null, null, cases.size(), 0, null, current,
				new Gap(null, null), cases);
	}

	private static BenchmarkView none(long current) {
		return new BenchmarkView(Tier.NONE, null, null, null, null, 0, 0, null, current, new Gap(null, null),
				List.of());
	}

	private static DealEvent deal(long price) {
		return aDealEvent().withPriceFirst(price).singleSite().build();
	}

	private static AlertPolicy policy(Long targetPrice) {
		return new AlertPolicy(targetPrice, null, null);
	}

	@Test
	void jackpotWinsOverEverything() {
		DealEvent d = aDealEvent().withPriceFirst(840_000L).outlier(OutlierFlag.LOWER).singleSite().build();

		AlertDecision r = evaluator.evaluate(d, sufficient(), policy(900_000L), params);

		assertThat(r.shouldAlert()).isTrue();
		assertThat(r.intensity()).isEqualTo(AlertIntensity.JACKPOT);
		// 나머지 충족 조건 병기(특가·목표가·괜찮은딜)
		assertThat(r.alsoSatisfied())
				.containsExactly(AlertIntensity.SPECIAL, AlertIntensity.TARGET, AlertIntensity.GOOD);
	}

	@Test
	void specialWhenAtOrBelowP25() {
		AlertDecision r = evaluator.evaluate(deal(840_000L), sufficient(), policy(null), params);

		assertThat(r.intensity()).isEqualTo(AlertIntensity.SPECIAL); // 840k <= P25 850k
		assertThat(r.alsoSatisfied()).containsExactly(AlertIntensity.GOOD); // 840k <= 기준가 890k
	}

	@Test
	void targetBeatsGoodWhenBothSatisfied() {
		// 880k: >P25(850k)이라 특가 아님, <=기준가(890k)=GOOD, <=목표가(900k)=TARGET
		AlertDecision r = evaluator.evaluate(deal(880_000L), sufficient(), policy(900_000L), params);

		assertThat(r.intensity()).isEqualTo(AlertIntensity.TARGET);
		assertThat(r.alsoSatisfied()).containsExactly(AlertIntensity.GOOD);
	}

	@Test
	void goodDealWhenAtOrBelowBenchmarkOnly() {
		AlertDecision r = evaluator.evaluate(deal(880_000L), sufficient(), policy(null), params);

		assertThat(r.intensity()).isEqualTo(AlertIntensity.GOOD);
		assertThat(r.alsoSatisfied()).isEmpty();
	}

	@Test
	void noAlertWhenAboveBenchmarkAndNoTargetNoJackpot() {
		AlertDecision r = evaluator.evaluate(deal(950_000L), sufficient(), policy(null), params);

		assertThat(r.shouldAlert()).isFalse();
		assertThat(r.intensity()).isEqualTo(AlertIntensity.NONE);
	}

	@Test
	void sparseUsesLowestCaseYardstickWithLabel() {
		BenchmarkView view = sparse(990_000L, 800_000L, 830_000L, 900_000L); // 최저 800k

		AlertDecision below = evaluator.evaluate(deal(790_000L), view, policy(null), params);
		assertThat(below.shouldAlert()).isTrue();
		assertThat(below.intensity()).isEqualTo(AlertIntensity.GOOD);
		assertThat(below.labels()).anyMatch(l -> l.contains("참고용"));

		AlertDecision above = evaluator.evaluate(deal(810_000L), view, policy(null), params);
		assertThat(above.shouldAlert()).isFalse();
	}

	@Test
	void noneTierColdStartJackpotFallbackWithLabel() {
		BenchmarkView view = none(1_000_000L); // 현재가 100만, 30% 컷 = 70만

		AlertDecision at = evaluator.evaluate(deal(700_000L), view, policy(null), params);
		assertThat(at.shouldAlert()).isTrue(); // 정확히 30% 싸다
		assertThat(at.labels()).anyMatch(l -> l.contains("기준 미확립"));

		AlertDecision just = evaluator.evaluate(deal(700_001L), view, policy(null), params);
		assertThat(just.shouldAlert()).isFalse();
	}

	@Test
	void verificationStatusLabelReflectsSourceSites() {
		DealEvent single = aDealEvent().withPriceFirst(840_000L).singleSite().build();
		DealEvent cross = aDealEvent().withPriceFirst(840_000L).crossVerified().build();

		assertThat(evaluator.evaluate(single, sufficient(), policy(null), params).labels())
				.anyMatch(l -> l.contains("미검증"));
		assertThat(evaluator.evaluate(cross, sufficient(), policy(null), params).labels())
				.anyMatch(l -> l.contains("검증"));
	}
}
