package dev.hogumeter.core.adapter.telegram;

import static dev.hogumeter.core.domain.deal.DealEventBuilder.aDealEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.hogumeter.core.adapter.telegram.TelegramAlertSender.Disposition;
import dev.hogumeter.core.adapter.telegram.TelegramApi.Button;
import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.domain.alert.AlertDecision;
import dev.hogumeter.core.domain.alert.AlertIntensity;
import dev.hogumeter.core.domain.benchmark.BenchmarkView;
import dev.hogumeter.core.domain.benchmark.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실 텔레그램 어댑터의 계약 — 실 네트워크 없이 fake로 검증한다(실 API 응답은 토큰 발급 후 수동 스파이크).
 * 요청 형태(포맷된 본문 · SEC-03 설정 chat) · SEC-08 상태 분류 · <b>어떤 실패에도 던지지 않음</b>(틱 보호).
 */
class TelegramAlertSenderTest {

	private static final class FakeApi implements TelegramApi {
		String sentChatId;
		String sentText;
		java.util.List<Button> sentButtons;
		int status = 200;
		RuntimeException transport;

		@Override
		public int sendMessage(String chatId, String text) {
			return sendMessage(chatId, text, List.of());
		}

		@Override
		public int sendMessage(String chatId, String text, java.util.List<Button> buttons) {
			if (transport != null) {
				throw transport;
			}
			this.sentChatId = chatId;
			this.sentText = text;
			this.sentButtons = buttons;
			return status;
		}
	}

	private static AlertMessage firstAlert() {
		var deal = aDealEvent().withPriceFirst(820_000).crossVerified()
				.withSourceUrl("https://ppomppu.test/1").build();
		var view = new BenchmarkView(Tier.SUFFICIENT, 890_000L, null,
				new BenchmarkView.PricePoint(850_000L, java.time.LocalDate.of(2026, 6, 1)), null, 2, 2, null, null,
				new BenchmarkView.Gap(null, null), List.of(), List.of());
		return new AlertMessage(deal, view, new AlertDecision(true, AlertIntensity.JACKPOT, List.of(), List.of()),
				null, "아이폰 17", "256GB", 42L);
	}

	@Test
	void realSenderReportsItDelivers() {
		assertThat(new TelegramAlertSender(new FakeApi(), "555000").delivers()).isTrue();
	}

	@Test
	void sendsFormattedBodyToConfiguredChat() {
		FakeApi api = new FakeApi();
		new TelegramAlertSender(api, "555000").send(firstAlert());

		assertThat(api.sentChatId).as("SEC-03: 설정된 chat로만 나간다").isEqualTo("555000");
		assertThat(api.sentText).contains("아이폰 17 256GB").contains("820,000원").contains("https://ppomppu.test/1");
	}

	/** Q-22: 딜 알림에 [무시] 버튼이 붙는다 — callback_data는 그 딜을 가리켜 사후학습으로 흐른다. */
	@Test
	void includesIgnoreButtonForTheDeal() {
		FakeApi api = new FakeApi();
		new TelegramAlertSender(api, "555000").send(firstAlert());

		assertThat(api.sentButtons).extracting(Button::callbackData).containsExactly("ignore:42");
	}

	/** SEC-08 순수 분류: 2xx 성공 / 5xx 일시장애(재시도 가능) / 그 외 4xx 거절(재시도 금지). */
	@ParameterizedTest
	@CsvSource({ "200,OK", "201,OK", "429,REJECTED", "403,REJECTED", "400,REJECTED", "500,TRANSIENT", "503,TRANSIENT" })
	void classifiesStatusPerSec08(int status, Disposition expected) {
		assertThat(TelegramAlertSender.classify(status)).isEqualTo(expected);
	}

	/** 거절(429·403)은 던지지 않는다 — 재시도로 봇 차단·레이트리밋을 두드리지 않는다(SEC-08). */
	@ParameterizedTest
	@CsvSource({ "429", "403", "400", "500", "503" })
	void neverThrowsOnAnyErrorStatus(int status) {
		FakeApi api = new FakeApi();
		api.status = status;

		assertThatCode(() -> new TelegramAlertSender(api, "555000").send(firstAlert())).doesNotThrowAnyException();
	}

	/** 네트워크 단절(transport 예외)도 던지지 않는다 — 한 알림 실패가 파이프라인 틱을 죽이지 않게. */
	@Test
	void networkTransportFailureDoesNotThrow() {
		FakeApi api = new FakeApi();
		api.transport = new RuntimeException("connection reset");

		assertThatCode(() -> new TelegramAlertSender(api, "555000").send(firstAlert())).doesNotThrowAnyException();
	}
}
