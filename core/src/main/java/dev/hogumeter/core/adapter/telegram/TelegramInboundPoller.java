package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.adapter.telegram.TelegramInboundApi.CallbackUpdate;
import dev.hogumeter.core.application.ReviewCallbackRouter;
import dev.hogumeter.core.application.ReviewCallbackRouter.CallbackResult;
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
 * <p><b>왜 롱폴링 아닌 짧은 주기 폴링인가</b>: 이 봇은 파이프라인 스케줄러와 <b>같은 @Scheduled 단일 스레드</b>를
 * 공유한다 — 롱폴링(getUpdates {@code timeout=25})은 그 스레드를 최대 25초 블로킹해 <b>파이프라인 틱을 민다.</b>
 * 대신 짧은 주기의 비블로킹 폴(timeout=0)을 쓴다. 주기는 <b>3초</b>다(Q-73 ②) — 버튼을 누르고 모달이 뜨기까지의
 * 지연을 최대 3초로 줄인다(예전 10초는 눌러도 한참 반응이 없어 "안 된 것처럼" 보였다). 1인용 저트래픽이라
 * 3초 폴(0.33 req/s)은 텔레그램 레이트리밋에 무의미하다. offset으로 처리한 업데이트를 확인해 재수신을 막는다.
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

	@Scheduled(fixedDelayString = "${telegram.poll-interval-ms:3000}",
			initialDelayString = "${telegram.poll-interval-ms:3000}")
	public void poll() {
		try {
			for (CallbackUpdate update : api.getUpdates(offset)) {
				CallbackResult result = router.route(update.fromChatId(), update.data());
				api.answerCallbackQuery(update.callbackQueryId(), result.reply(), true); // 모달로 — 결과를 놓치지 않게(Q-73 ①)
				// 상태가 바뀌었으면 원 메시지를 편집해 버튼을 없애고 결과를 남긴다 — 나중에 봐도 처리됐음을 안다(Q-73 ③).
				// messageId가 없으면(옛 메시지 등) 건너뛴다. 편집 실패는 이 try의 catch가 삼켜 다음 콜백을 안 죽인다.
				if (result.editMessage() && update.messageId() != 0) {
					String resolved = update.messageText() == null ? result.reply()
							: update.messageText() + "\n\n" + result.reply();
					api.editMessageText(String.valueOf(update.messageChatId()), update.messageId(), resolved);
				}
				offset = Math.max(offset, update.updateId() + 1); // 처리한 것 다음부터 — 재수신 방지
			}
		}
		catch (RuntimeException failure) {
			log.warn("텔레그램 인바운드 폴 실패 — 다음 주기에 재시도합니다", failure);
		}
	}
}
