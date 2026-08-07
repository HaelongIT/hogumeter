package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * BE-04(코드리뷰 20260806) — {@code getUpdates}가 HTTP 상태를 확인하지 않고 바로 본문을 파싱해, 401/403 같은
 * 실패가 완전히 침묵했다. 로컬 루프백 서버(127.0.0.1, 실 네트워크 호출 아님)로 실제 상태 코드 처리를
 * 검증한다 — 이 클래스는 프로덕션에서 baseUrl을 {@code https://api.telegram.org}로 고정하므로, 테스트
 * 전용 패키지-프라이빗 생성자로 로컬 서버를 가리키게 한다.
 */
class HttpTelegramApiTest {

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	private HttpTelegramApi apiPointingAt(int statusCode, String body) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(statusCode, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		try {
			Constructor<HttpTelegramApi> ctor = HttpTelegramApi.class.getDeclaredConstructor(String.class, boolean.class);
			ctor.setAccessible(true);
			return ctor.newInstance(baseUrl, true);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void getUpdatesParsesCallbacksOn200() throws IOException {
		HttpTelegramApi api = apiPointingAt(200, """
				{"result":[{"update_id":1,"callback_query":{"id":"q1","from":{"id":42},"data":"promote:5"}}]}""");

		var updates = api.getUpdates(0);

		assertThat(updates).hasSize(1);
		assertThat(updates.get(0).data()).isEqualTo("promote:5");
	}

	/**
	 * BE-22(코드리뷰 20260806) — 콜백이 아닌 업데이트(일반 텍스트·/start 등)는 예전엔 결과 목록에서
	 * 완전히 빠졌다. 그러면 폴러가 그 update_id로 offset을 전진시킬 방법이 없어, 사용자가 봇에게
	 * 일반 메시지를 보내면 그 update_id가 매 폴마다 계속 재조회된다(부작용은 없지만 낭비 트래픽).
	 * 콜백이 아닌 항목도 결과에 실어(data=null) 폴러가 offset만 전진시킬 수 있게 한다.
	 */
	@Test
	void getUpdatesIncludesNonCallbackUpdatesSoOffsetCanAdvance() throws IOException {
		HttpTelegramApi api = apiPointingAt(200, """
				{"result":[{"update_id":1,"message":{"text":"/start"}},
				            {"update_id":2,"callback_query":{"id":"q1","from":{"id":42},"data":"promote:5"}}]}""");

		var updates = api.getUpdates(0);

		assertThat(updates).hasSize(2);
		assertThat(updates.get(0).updateId()).isEqualTo(1);
		assertThat(updates.get(0).data()).as("콜백이 아니면 data가 없다 — 폴러가 라우팅을 건너뛰는 신호").isNull();
		assertThat(updates.get(1).updateId()).isEqualTo(2);
		assertThat(updates.get(1).data()).isEqualTo("promote:5");
	}

	@Test
	void getUpdatesThrowsOn401InsteadOfSilentlyReturningEmpty() throws IOException {
		HttpTelegramApi api = apiPointingAt(401, "{\"ok\":false,\"description\":\"Unauthorized\"}");

		// 예전엔 여기서 빈 목록을 조용히 반환했다 — 이제는 예외를 던져 호출자가 실패를 알게 한다.
		assertThatThrownBy(() -> api.getUpdates(0)).isInstanceOf(RuntimeException.class);
	}

	@Test
	void getUpdatesThrowsOn500() throws IOException {
		HttpTelegramApi api = apiPointingAt(500, "internal error");

		assertThatThrownBy(() -> api.getUpdates(0)).isInstanceOf(RuntimeException.class);
	}

	/** answerCallbackQuery·editMessageText는 폴러를 죽이면 안 돼(void) 던지지 않지만, 실패를 로그로 남긴다. */
	@Test
	void answerCallbackQueryDoesNotThrowOnFailureButStillCompletes() throws IOException {
		AtomicInteger calls = new AtomicInteger();
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			calls.incrementAndGet();
			byte[] bytes = "forbidden".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(403, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
		HttpTelegramApi api;
		try {
			Constructor<HttpTelegramApi> ctor = HttpTelegramApi.class.getDeclaredConstructor(String.class, boolean.class);
			ctor.setAccessible(true);
			api = ctor.newInstance(baseUrl, true);
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}

		api.answerCallbackQuery("q1", "처리됨", false); // 던지지 않는다 — 폴러가 다음 콜백을 계속 처리해야 한다

		assertThat(calls.get()).isEqualTo(1); // 실제로 호출은 갔다(로그만으로 남기고 삼키지 않았는지는 수동 확인)
	}
}
