package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.AlertMessage;
import dev.hogumeter.core.application.port.out.AlertSender;
import dev.hogumeter.core.application.port.out.UsedAlertMessage;
import dev.hogumeter.core.application.port.out.UsedAlertSender;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AL-05 실 텔레그램 발송 어댑터. {@code telegram.enabled=true}일 때만 활성(그 외에는 {@link StubAlertSender}).
 * 봇 토큰·chat_id는 <b>사용자가 .env에 채운다</b> — 이 코드는 그 값을 설정에서 읽을 뿐, 저장소에 담지 않는다.
 * 본문은 {@link AlertMessageFormatter}로 스텁과 <b>같은 포맷</b>을 쓴다.
 *
 * <p><b>SEC-03</b>: 발송 대상은 설정된 {@code telegram.chat-id} 하나뿐이다(허용된 chat로만 나간다).
 * 인바운드(인라인 버튼 콜백)의 chat_id 화이트리스트 검증은 그 기능(Q-15 버튼)이 생길 때 함께 든다.
 *
 * <p><b>SEC-08</b>: 외부의 거절을 재시도 가능한 오류로 뭉개지 않는다 — 응답을 {@link Disposition}으로 가른다.
 * 4xx(403 차단·429 레이트리밋·400 잘못된 요청)는 <b>거절</b>이라 재시도하지 않고 크게 남긴다(설정·차단 확인이
 * 필요하지 재시도로 풀리지 않는다). 5xx·네트워크 단절은 <b>일시장애</b>다. {@code send}는 <b>던지지 않는다</b> —
 * 한 알림의 실패가 파이프라인 틱을 죽이지 않게. (일시장애 백오프 큐는 후속 — 지금은 그 한 통을 놓칠 수 있다.)
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramAlertSender implements AlertSender, UsedAlertSender {

	private static final Logger log = LoggerFactory.getLogger(TelegramAlertSender.class);

	enum Disposition {
		OK,
		REJECTED,
		TRANSIENT
	}

	private final TelegramApi api;
	private final String chatId;
	private final AlertMessageFormatter formatter = new AlertMessageFormatter();

	private final UsedAlertMessageFormatter usedFormatter = new UsedAlertMessageFormatter();

	@org.springframework.beans.factory.annotation.Autowired
	public TelegramAlertSender(@Value("${telegram.bot-token:}") String botToken,
			@Value("${telegram.chat-id:}") String chatId) {
		this(new HttpTelegramApi(requireConfig(botToken, "telegram.bot-token")),
				requireConfig(chatId, "telegram.chat-id"));
	}

	/** 테스트 seam — fake TelegramApi를 주입해 실 네트워크 없이 요청 형태·상태 처리(SEC-08)를 검증한다. */
	TelegramAlertSender(TelegramApi api, String chatId) {
		this.api = api;
		this.chatId = chatId;
	}

	private static String requireConfig(String value, String key) {
		if (value == null || value.isBlank()) {
			// enabled=true인데 토큰·chat이 비었다 — 조용히 스텁처럼 굴지 않고 기동을 멈춰 설정 누락을 드러낸다.
			throw new IllegalStateException("telegram.enabled=true인데 " + key + "가 비어 있습니다 (.env 확인)");
		}
		return value;
	}

	@Override
	public boolean delivers() {
		return true; // 실 전송 — 사용자에게 닿는다(스텁과 달리)
	}

	@Override
	public void send(AlertMessage message) {
		String text = formatter.format(message);
		// [🔕무시] 버튼(Q-22 사후학습) — 누르면 이 딜을 노이즈로 기록하고 빈출 토큰을 제외 키워드 후보로 배운다.
		List<TelegramApi.Button> buttons = message.dealEventId() == null
				? List.of()
				: List.of(new TelegramApi.Button("🔕 무시", "ignore:" + message.dealEventId()));
		post(text, buttons);
	}

	/**
	 * USED-03 중고 알림. 버튼은 아직 없다 — [무시]는 신품 딜의 사후학습(Q-22)에 묶인 콜백이라
	 * 중고 매물에 그대로 붙이면 없는 딜 id를 가리킨다. 중고용 액션이 정해지면 그때 단다.
	 */
	@Override
	public void sendUsed(UsedAlertMessage message) {
		post(usedFormatter.format(message), List.of());
	}

	/** 전송·상태 분류는 알림 부류와 무관하다 — 한 곳에 둔다(SEC-08 처리가 갈라지지 않게). */
	private void post(String text, List<TelegramApi.Button> buttons) {
		int status;
		try {
			status = api.sendMessage(chatId, text, buttons);
		}
		catch (RuntimeException transport) {
			// 네트워크가 닿지 못함 — 일시장애로 다룬다. 던지지 않는다(틱을 죽이지 않게).
			log.warn("SEC-08 텔레그램 전송 실패(일시장애·transport) — 이 알림은 이번엔 못 갔습니다", transport);
			return;
		}
		switch (classify(status)) {
			case OK -> { /* 발송됨 */ }
			case REJECTED -> log.error(
					"SEC-08 텔레그램 거절 status={} — 재시도하지 않습니다(봇 차단·레이트리밋·잘못된 설정). .env·차단 확인 필요",
					status);
			case TRANSIENT -> log.warn("SEC-08 텔레그램 일시장애 status={} — 이 알림은 이번엔 못 갔습니다", status);
		}
	}

	/** 순수 분류(테스트 대상). 2xx=성공, 5xx=일시장애(재시도 가능), 그 외(4xx)=거절(재시도 금지). */
	static Disposition classify(int status) {
		if (status >= 200 && status < 300) {
			return Disposition.OK;
		}
		if (status >= 500 && status < 600) {
			return Disposition.TRANSIENT;
		}
		return Disposition.REJECTED;
	}
}
