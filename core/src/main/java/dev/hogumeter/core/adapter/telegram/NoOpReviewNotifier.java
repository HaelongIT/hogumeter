package dev.hogumeter.core.adapter.telegram;

import dev.hogumeter.core.application.port.out.ReviewNotifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 텔레그램 미설정(기본)이면 미상 큐 항목을 텔레그램으로 보내지 않는다 — web 미상 큐가 이미 그 창구다.
 * 로그도 남기지 않는다(딜 알림 스텁과 달리 여기선 로그가 소음이다). {@code telegram.enabled=true}면
 * {@link TelegramReviewNotifier}가 대체한다.
 */
@Component
@ConditionalOnProperty(name = "telegram.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpReviewNotifier implements ReviewNotifier {

	@Override
	public void notify(long reviewItemId, String summary, boolean promotable) {
		// no-op — web 미상 큐로 본다
	}
}
