package dev.hogumeter.core.adapter.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link TelegramApi}(아웃바운드)·{@link TelegramInboundApi}(인바운드)의 실 HTTP 구현. 텔레그램 Bot API
 * {@code sendMessage}·{@code getUpdates}·{@code answerCallbackQuery}를 친다. 얇은 글루다 — 요청 형태·상태
 * 처리(SEC-08)·라우팅은 {@link TelegramAlertSender}·{@code ReviewCallbackRouter}가 fake로 검증하고, 이 클래스의
 * 실 응답 파싱은 토큰 발급 후 수동 스파이크로만 확인된다(실 네트워크 테스트 금지).
 *
 * <p><b>form-urlencoded</b>로 보낸다 — 본문에 줄바꿈·한글이 있어 JSON 이스케이프를 피한다. <b>토큰은 URL에만</b>
 * 있고(텔레그램 규약 {@code /bot<token>/}) 어디에도 로그하지 않는다(SEC-01 — URL 로깅은 토큰 유출이다).
 * getUpdates 응답은 {@code Map}으로 파싱한다 — JsonNode API 불확실성을 피하고 방어적으로 탐색한다.
 */
public class HttpTelegramApi implements TelegramApi, TelegramInboundApi {

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final JsonMapper json = new JsonMapper();
	private final String baseUrl;

	public HttpTelegramApi(String botToken) {
		this.baseUrl = "https://api.telegram.org/bot" + botToken;
	}

	@Override
	public int sendMessage(String chatId, String text) {
		return post(baseUrl + "/sendMessage", "chat_id=" + enc(chatId) + "&text=" + enc(text));
	}

	/** form-urlencoded POST → HTTP 상태. 네트워크 단절은 {@link TelegramTransportException}(발신자가 일시장애로 다룬다). */
	private int post(String url, String formBody) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8))
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		}
		catch (IOException e) {
			throw new TelegramTransportException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TelegramTransportException(e);
		}
	}

	@Override
	public int sendMessage(String chatId, String text, List<Button> buttons) {
		if (buttons == null || buttons.isEmpty()) {
			return sendMessage(chatId, text);
		}
		// reply_markup은 JSON이어야 한다(form 값 안에 JSON을 넣는다). callback_data는 라우터가 파싱할 "promote:123".
		StringBuilder markup = new StringBuilder("{\"inline_keyboard\":[[");
		for (int i = 0; i < buttons.size(); i++) {
			Button b = buttons.get(i);
			markup.append(i == 0 ? "" : ",")
					.append("{\"text\":").append(json.writeValueAsString(b.text()))
					.append(",\"callback_data\":").append(json.writeValueAsString(b.callbackData())).append("}");
		}
		markup.append("]]}");
		String body = "chat_id=" + enc(chatId) + "&text=" + enc(text) + "&reply_markup=" + enc(markup.toString());
		return post(baseUrl + "/sendMessage", body);
	}

	@Override
	public List<CallbackUpdate> getUpdates(long offset) {
		// timeout=0: 짧은 주기 폴링(롱폴링 블로킹 스레드를 피한다). 콜백만 받으면 되지만 필터는 파싱에서.
		String body = "offset=" + offset + "&timeout=0";
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/getUpdates"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(15))
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return parseCallbacks(response.body());
		}
		catch (IOException e) {
			throw new TelegramTransportException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TelegramTransportException(e);
		}
	}

	@Override
	public void answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
		String body = "callback_query_id=" + enc(callbackQueryId) + "&text=" + enc(text);
		if (showAlert) {
			body += "&show_alert=true"; // 일시 토스트 대신 모달 — 눌러 닫아야 하니 놓치기 어렵다(Q-73)
		}
		post(baseUrl + "/answerCallbackQuery", body);
	}

	/**
	 * getUpdates 응답에서 <b>콜백 업데이트만</b> 뽑는다. Map으로 방어적 탐색 — 텔레그램 JSON 형태가 흔들려도
	 * 없는 필드는 건너뛴다(전체 폴을 죽이지 않는다). 형태: {@code {"result":[{"update_id":N,"callback_query":
	 * {"id":..,"from":{"id":..},"data":".."}}]}}.
	 */
	private List<CallbackUpdate> parseCallbacks(String responseBody) {
		List<CallbackUpdate> out = new ArrayList<>();
		Object root = json.readValue(responseBody, Map.class);
		if (!(root instanceof Map<?, ?> map) || !(map.get("result") instanceof List<?> updates)) {
			return out;
		}
		for (Object item : updates) {
			if (!(item instanceof Map<?, ?> update) || !(update.get("callback_query") instanceof Map<?, ?> cq)) {
				continue; // 콜백이 아닌 업데이트는 무시
			}
			long fromChatId = (cq.get("from") instanceof Map<?, ?> from && from.get("id") instanceof Number id)
					? id.longValue() : 0L;
			long updateId = update.get("update_id") instanceof Number n ? n.longValue() : 0L;
			String data = cq.get("data") instanceof String s ? s : null;
			String queryId = cq.get("id") instanceof String s ? s : null;
			out.add(new CallbackUpdate(updateId, fromChatId, data, queryId));
		}
		return out;
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** 전송 계층 실패(네트워크 단절 등). 예외 메시지에 URL을 담지 않는다 — 토큰 유출 방지(SEC-01). */
	static final class TelegramTransportException extends RuntimeException {
		TelegramTransportException(Throwable cause) {
			super("telegram transport failure: " + cause.getClass().getSimpleName());
		}
	}
}
