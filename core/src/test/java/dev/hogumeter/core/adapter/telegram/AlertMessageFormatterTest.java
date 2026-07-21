package dev.hogumeter.core.adapter.telegram;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.domain.alert.AlertDecision;
import dev.hogumeter.core.domain.alert.AlertIntensity;
import dev.hogumeter.core.domain.alert.FollowUpKind;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.DealEvent;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AL-05 알림 본문 포맷터의 순수 계약. 정직성(SPARSE 금액 금지)·조건 태그 노출(Q-46 ①)·이름 부재 처리를
 * 단위로 잠근다 — 문구가 조용히 갈라지지 않게. 스텁·텔레그램 어댑터가 이 결과를 그대로 보낸다.
 */
class AlertMessageFormatterTest {

	private final AlertMessageFormatter formatter = new AlertMessageFormatter();

	private static BenchmarkView sufficient(long benchmark, long lowest, int m) {
		return new BenchmarkView(Tier.SUFFICIENT, benchmark, null,
				new BenchmarkView.PricePoint(lowest, LocalDate.of(2026, 6, 10)), null, m, m, null, null,
				new BenchmarkView.Gap(null, null), List.of());
	}

	private static BenchmarkView sparse(int n) {
		return new BenchmarkView(Tier.SPARSE, null, null, null, null, n, 0, null, null,
				new BenchmarkView.Gap(null, null), List.of());
	}

	private static AlertMessage first(DealEvent deal, BenchmarkView view, AlertIntensity intensity,
			List<AlertIntensity> also, List<String> labels) {
		return new AlertMessage(deal, view, new AlertDecision(true, intensity, also, labels), null,
				"아이폰 17", "256GB", null);
	}

	@Test
	void firstAlertShowsSubjectPriceGapVerificationAndLink() {
		DealEvent deal = aDealEvent().withPriceFirst(820_000).crossVerified()
				.withSourceUrl("https://ppomppu.test/123").build();

		String out = formatter.format(first(deal, sufficient(890_000, 850_000, 2),
				AlertIntensity.JACKPOT, List.of(), List.of()));

		assertThat(out).contains("🔥");
		assertThat(out).contains("아이폰 17 256GB");
		assertThat(out).contains("820,000원");
		assertThat(out).contains("기준가 890,000원보다 70,000원(7.9%) 쌈");
		assertThat(out).contains("역대최저 850,000원");
		assertThat(out).contains("2개 사이트 교차검증");
		assertThat(out).contains("https://ppomppu.test/123");
	}

	/** 절대 원칙 1: SPARSE면 기준가 금액을 짓지 않는다. 딜 자신의 가격은 실제라 보이되, 기준가 숫자는 없다. */
	@Test
	void sparseFirstAlertClaimsNoBenchmarkAmount() {
		DealEvent deal = aDealEvent().withPriceFirst(820_000).build();

		String out = formatter.format(first(deal, sparse(3), AlertIntensity.SPECIAL, List.of(), List.of()));

		assertThat(out).contains("표본 3건");
		assertThat(out).doesNotContainPattern("기준가 [0-9][0-9,]*원"); // 지어낸 기준가 금액 없음
	}

	@Test
	void alsoSatisfiedAndLabelsAreAppended() {
		DealEvent deal = aDealEvent().withPriceFirst(820_000).build();

		String out = formatter.format(first(deal, sufficient(890_000, 850_000, 2),
				AlertIntensity.SPECIAL, List.of(AlertIntensity.TARGET), List.of("표본 5건 참고용")));

		assertThat(out).contains("목표가 이하"); // alsoSatisfied 병기(아이콘 뗀 문구)
		assertThat(out).contains("표본 5건 참고용"); // 딱지 병기
	}

	/** Q-46 ①: 조건 태그를 본문에 밝힌다. 배송비미상은 실 결제가가 더 높을 수 있음을 덧붙인다. */
	@Test
	void conditionTagsAreSurfacedWithShippingCaveat() {
		DealEvent deal = aDealEvent().withPriceFirst(16_450).appliedConditions("배송비미상").build();

		String out = formatter.format(first(deal, sufficient(20_000, 18_000, 2),
				AlertIntensity.GOOD, List.of(), List.of()));

		assertThat(out).contains("⚠️ 조건: 배송비미상");
		assertThat(out).contains("실 결제가는 표시가보다 높을 수 있음");
	}

	@Test
	void endedFollowUpShowsEndedHeadlineAndLatestPrice() {
		DealEvent deal = aDealEvent().withPriceFirst(700_000).withSourceUrl("https://ruliweb.test/9").build();
		AlertMessage m = new AlertMessage(deal, null, null, FollowUpKind.ENDED, "아이폰 17", "256GB", null);

		String out = formatter.format(m);

		assertThat(out).contains("⛔ 종료됨");
		assertThat(out).contains("아이폰 17 256GB");
		assertThat(out).contains("700,000원");
		assertThat(out).contains("https://ruliweb.test/9");
	}

	@Test
	void priceChangedFollowUpAnnotatesLatestPrice() {
		DealEvent deal = aDealEvent().withPriceFirst(900_000).withPrices(880_000, 900_000, 880_000).build();
		AlertMessage m = new AlertMessage(deal, null, null, FollowUpKind.PRICE_CHANGED, "아이폰 17", "256GB", null);

		String out = formatter.format(m);

		assertThat(out).contains("🔁 가격 변동");
		assertThat(out).contains("최신가 880,000원");
	}

	/** 이름을 못 찾으면 "대상 미상" — 지어내지 않고 그 사실을 그린다(원문 링크로 넘긴다). */
	@Test
	void missingNameShowsUnknownSubject() {
		DealEvent deal = aDealEvent().withPriceFirst(820_000).build();
		AlertMessage m = new AlertMessage(deal, sufficient(890_000, 850_000, 2),
				new AlertDecision(true, AlertIntensity.GOOD, List.of(), List.of()), null, null, null, null);

		String out = formatter.format(m);

		assertThat(out).contains("대상 미상");
	}
}
