package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.adapter.telegram.TelegramInboundApi.CallbackUpdate;
import dev.hogumeter.core.application.ReviewCallbackRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 인라인 버튼 콜백을 <b>짧은 주기 폴링</b>(getUpdates)으로 받아 {@link ReviewCallbackRouter}로 넘긴다
 * (Q-15 승격·기각을 텔레그램에서). {@code telegram.enabled=true}일 때만 뜬다 — 기본(스텁)은 폴링하지 않아
 * 실 네트워크가 사용자 opt-in 없이는 일어나지 않는다.
 *
 * <p><b>왜 롱폴링 아닌 짧은 주기 폴링인가</b>: 1인용 저트래픽이라 10초 주기면 버튼 응답이 충분히 즉각적이고,
 * 블로킹 스레드(롱폴링)보다 단순하다. offset으로 처리한 업데이트를 확인해 재수신을 막는다.
 *
 * <p><b>던지지 않는다</b>: 한 폴의 실패(네트워크·파싱)가 스케줄러를 죽이지 않게 잡고 다음 주기에 재시도한다.
 * {@code getUpdates} 실 파싱은 fake로만 검증된다(실 네트워크 테스트 금지) — 실 응답은 토큰 발급 후 수동 스파이크.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "true")
public class TelegramInboundPoller {

	private static final Logger log = LoggerFactory.getLogger(TelegramInboundPoller.class);

	private final TelegramInboundApi api;
	private final ReviewCallbackRouter router;
	private long offset;

	@Autowired
	public TelegramInboundPoller(@Value("${telegram.bot-token:}") String botToken, ReviewCallbackRouter router) {
		this(new HttpTelegramApi(botToken), router);
	}

	/** 테스트 seam — fake 인바운드 api를 주입. */
	TelegramInboundPoller(TelegramInboundApi api, ReviewCallbackRouter router) {
		this.api = api;
		this.router = router;
	}

	@Scheduled(fixedDelayString = "${telegram.poll-interval-ms:10000}",
			initialDelayString = "${telegram.poll-interval-ms:10000}")
	public void poll() {
		try {
			for (CallbackUpdate update : api.getUpdates(offset)) {
				String reply = router.route(update.fromChatId(), update.data());
				api.answerCallbackQuery(update.callbackQueryId(), reply);
				offset = Math.max(offset, update.updateId() + 1); // 처리한 것 다음부터 — 재수신 방지
			}
		}
		catch (RuntimeException failure) {
			log.warn("텔레그램 인바운드 폴 실패 — 다음 주기에 재시도합니다", failure);
		}
	}
}
