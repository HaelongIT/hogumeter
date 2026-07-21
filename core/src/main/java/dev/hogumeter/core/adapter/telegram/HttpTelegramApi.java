package dev.hogumeter.core.adapter.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link TelegramApi}의 실 HTTP 구현. 텔레그램 Bot API {@code sendMessage}를 친다.
 * 얇은 글루다 — 요청 형태·상태 처리(SEC-08)는 {@link TelegramAlertSender}가 fake로 검증하고,
 * 이 클래스의 실 응답은 토큰 발급 후 수동 스파이크로만 확인된다(실 네트워크 테스트 금지).
 *
 * <p><b>form-urlencoded</b>로 보낸다 — 본문에 줄바꿈·한글이 있어 JSON 이스케이프를 피한다. <b>토큰은 URL에만</b>
 * 있고(텔레그램 규약 {@code /bot<token>/}) 어디에도 로그하지 않는다(SEC-01 — URL 로깅은 토큰 유출이다).
 */
public class HttpTelegramApi implements TelegramApi {

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private final String baseUrl;

	public HttpTelegramApi(String botToken) {
		this.baseUrl = "https://api.telegram.org/bot" + botToken;
	}

	@Override
	public int sendMessage(String chatId, String text) {
		String body = "chat_id=" + enc(chatId) + "&text=" + enc(text);
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/sendMessage"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.timeout(Duration.ofSeconds(10))
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();
		try {
			return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
		}
		catch (IOException e) {
			throw new TelegramTransportException(e); // 네트워크 단절 — 발신자가 일시장애로 다룬다
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TelegramTransportException(e);
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	/** 전송 계층 실패(네트워크 단절 등). 예외 메시지에 URL을 담지 않는다 — 토큰 유출 방지(SEC-01). */
	static final class TelegramTransportException extends RuntimeException {
		TelegramTransportException(Throwable cause) {
			super("telegram sendMessage transport failure: " + cause.getClass().getSimpleName());
		}
	}
}
