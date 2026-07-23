package dev.hogumeter.core.application;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.domain.BenchmarkParams;
import dev.hogumeter.core.domain.alert.AlertEvaluator;
import dev.hogumeter.core.domain.alert.AlertGate;
import dev.hogumeter.core.domain.alert.AlertIntensity;
import dev.hogumeter.core.domain.alert.AlertPolicy;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.Gap;
import dev.hogumeter.core.domain.benchmark.BenchmarkView.PricePoint;
import dev.hogumeter.core.domain.benchmark.Tier;
import dev.hogumeter.core.domain.deal.OutlierFlag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AL: 발송 어댑터를 fake로 대체해 "무엇을 보내기로 했는가"만 검증(docs/12 테스트 포인트).
 * 실제 텔레그램 전송은 어댑터(정지 조건 — 토큰 필요). 여기선 AlertSender 포트에 fake 주입.
 */
class AlertDispatcherTest {

	private final BenchmarkParams params = BenchmarkParams.defaults();

	private static final class FakeAlertSender implements AlertSender {
		final List<AlertMessage> sent = new ArrayList<>();

		@Override
		public void send(AlertMessage message) {
			sent.add(message);
		}
	}

	private static BenchmarkView sufficient() {
		return new BenchmarkView(Tier.SUFFICIENT, 890_000L, 850_000L,
				new PricePoint(820_000L, LocalDate.of(2026, 6, 1)), null,
				7, 3, null, 990_000L, new Gap(null, null), List.of(), List.of());
	}

	private static Clock clockAtHour(int hour) {
		return Clock.fixed(Instant.parse(String.format("2026-07-05T%02d:00:00Z", hour)), ZoneOffset.UTC);
	}

	private AlertDispatcher dispatcher(FakeAlertSender sender) {
		return new AlertDispatcher(new AlertEvaluator(), new AlertGate(), sender,
				id -> new VariantNaming.Naming("아이폰 17", "256GB"));
	}

	@Test
	void jackpotIsSentEvenDuringQuietHours() {
		FakeAlertSender sender = new FakeAlertSender();
		var deal = aDealEvent().withPriceFirst(700_000L).outlier(OutlierFlag.LOWER).singleSite().build();

		DispatchOutcome outcome = dispatcher(sender).dispatch(
				deal, sufficient(), new AlertPolicy(null, 23, 8), params, clockAtHour(2), 42L);

		assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
		assertThat(sender.sent).hasSize(1);
		assertThat(sender.sent.get(0).decision().intensity()).isEqualTo(AlertIntensity.JACKPOT);
	}

	/** AL-05: 발송 메시지가 제품/variant 이름을 싣는다 — 배선이 끊기면 null이라, 이 관통 테스트가 잡는다. */
	@Test
	void sentMessageCarriesResolvedProductAndVariantName() {
		FakeAlertSender sender = new FakeAlertSender();
		AlertDispatcher dispatcher = new AlertDispatcher(new AlertEvaluator(), new AlertGate(), sender,
				id -> new VariantNaming.Naming("갤럭시 S26", "울트라"));
		var deal = aDealEvent().withVariantId(42L).withPriceFirst(700_000L)
				.outlier(OutlierFlag.LOWER).singleSite().build();

		dispatcher.dispatch(deal, sufficient(), new AlertPolicy(null, null, null), params, clockAtHour(12), 42L);

		assertThat(sender.sent.get(0).productName()).isEqualTo("갤럭시 S26");
		assertThat(sender.sent.get(0).variantLabel()).isEqualTo("울트라");
	}

	@Test
	void goodDealIsHeldDuringQuietHoursAndNotSent() {
		FakeAlertSender sender = new FakeAlertSender();
		var deal = aDealEvent().withPriceFirst(880_000L).singleSite().build();

		DispatchOutcome outcome = dispatcher(sender).dispatch(
				deal, sufficient(), new AlertPolicy(null, 23, 8), params, clockAtHour(2), 42L);

		assertThat(outcome).isEqualTo(DispatchOutcome.HELD);
		assertThat(sender.sent).isEmpty();
	}

	@Test
	void nonQualifyingDealIsNotSent() {
		FakeAlertSender sender = new FakeAlertSender();
		var deal = aDealEvent().withPriceFirst(950_000L).singleSite().build();

		DispatchOutcome outcome = dispatcher(sender).dispatch(
				deal, sufficient(), new AlertPolicy(null, null, null), params, clockAtHour(12), 42L);

		assertThat(outcome).isEqualTo(DispatchOutcome.NO_ALERT);
		assertThat(sender.sent).isEmpty();
	}
}
