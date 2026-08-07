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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger log = LoggerFactory.getLogger(HttpTelegramApi.class);

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final JsonMapper json = new JsonMapper();
	private final String baseUrl;

	public HttpTelegramApi(String botToken) {
		this.baseUrl = "https://api.telegram.org/bot" + botToken;
	}

	/** 테스트 seam — 로컬 HTTP 서버로 실 상태코드 처리를 검증한다(실 네트워크 호출 없음, 127.0.0.1만). */
	HttpTelegramApi(String baseUrl, boolean rawBaseUrl) {
		this.baseUrl = baseUrl;
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

	/**
	 * BE-04(코드리뷰 20260806): 예전엔 상태 코드를 안 보고 바로 본문을 파싱했다 — 토큰 무효화·차단(401/403)
	 * 시 본문에 {@code result} 키가 없어 {@code parseCallbacks}가 조용히 빈 목록을 반환하고, 호출자
	 * ({@code TelegramInboundPoller.poll()})의 {@code catch (RuntimeException)}은 예외 자체가 없어
	 * 트리거되지 않았다 — 인바운드(승격/기각/무시 버튼) 채널이 흔적 없이 멈추는 경로였다. 2xx가 아니면
	 * 예외를 던져 그 catch가 로그를 남기게 한다.
	 */
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
			if (response.statusCode() / 100 != 2) {
				// 메시지에 URL·토큰을 담지 않는다(SEC-01) — 상태 코드만.
				throw new TelegramTransportException(response.statusCode());
			}
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
		warnIfFailed("answerCallbackQuery", post(baseUrl + "/answerCallbackQuery", body));
	}

	@Override
	public void editMessageText(String chatId, long messageId, String text) {
		// reply_markup을 빈 inline_keyboard로 보내 버튼을 제거한다(처리 후 다시 못 누르게 + "처리됨"을 남긴다, Q-73 ③).
		String body = "chat_id=" + enc(chatId) + "&message_id=" + messageId
				+ "&text=" + enc(text) + "&reply_markup=" + enc("{\"inline_keyboard\":[]}");
		warnIfFailed("editMessageText", post(baseUrl + "/editMessageText", body));
	}

	/** BE-04: 실패해도 폴러를 죽이지 않는 호출들(void 반환)은 최소한 로그로 남긴다 — 완전한 침묵을 없앤다. */
	private void warnIfFailed(String operation, int statusCode) {
		if (statusCode / 100 != 2) {
			log.warn("텔레그램 {} 실패: status={}", operation, statusCode);
		}
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
			if (!(item instanceof Map<?, ?> update)) {
				continue; // 형태가 아예 안 맞는 항목만 무시
			}
			// BE-22(코드리뷰 20260806): 콜백이 아닌 업데이트(일반 텍스트·/start 등)도 결과에 싣는다
			// (data=null) — 그래야 폴러가 그 update_id로 offset을 전진시킬 수 있다. 예전엔 여기서
			// 완전히 걸러 offset이 정체돼, 사용자가 일반 메시지를 보내면 그 update_id가 매 폴(3초)마다
			// 계속 재조회됐다(부작용 없는 낭비 트래픽).
			if (!(update.get("callback_query") instanceof Map<?, ?> cq)) {
				long bareUpdateId = update.get("update_id") instanceof Number n ? n.longValue() : 0L;
				out.add(new CallbackUpdate(bareUpdateId, 0L, null, null, 0L, 0L, null));
				continue;
			}
			long fromChatId = (cq.get("from") instanceof Map<?, ?> from && from.get("id") instanceof Number id)
					? id.longValue() : 0L;
			long updateId = update.get("update_id") instanceof Number n ? n.longValue() : 0L;
			String data = cq.get("data") instanceof String s ? s : null;
			String queryId = cq.get("id") instanceof String s ? s : null;
			// 편집 대상(버튼이 달린 원 메시지)의 좌표·본문. 없으면 0/null → 폴러가 편집을 건너뛴다(Q-73 ③).
			Map<?, ?> message = cq.get("message") instanceof Map<?, ?> m ? m : null;
			long messageChatId = (message != null && message.get("chat") instanceof Map<?, ?> chat
					&& chat.get("id") instanceof Number cid) ? cid.longValue() : 0L;
			long messageId = (message != null && message.get("message_id") instanceof Number mid)
					? mid.longValue() : 0L;
			String messageText = (message != null && message.get("text") instanceof String t) ? t : null;
			out.add(new CallbackUpdate(updateId, fromChatId, data, queryId, messageChatId, messageId, messageText));
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

		/** BE-04: 2xx가 아닌 상태 코드도 전송 실패로 취급 — 메시지엔 상태 코드만(URL·토큰 없음, SEC-01). */
		TelegramTransportException(int statusCode) {
			super("telegram transport failure: status=" + statusCode);
		}
	}
}
