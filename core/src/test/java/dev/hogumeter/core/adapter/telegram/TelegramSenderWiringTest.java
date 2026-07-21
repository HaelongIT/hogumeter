package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
import dev.hogumeter.core.application.ReviewCallbackRouter;
import dev.hogumeter.core.application.port.out.AdminNotifier;
import dev.hogumeter.core.application.port.out.AlertSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 안전-핵심 배선: {@code telegram.enabled=true}면 실 어댑터가 뜬다. 기본(미설정)이 스텁인 것은 다른 모든
 * {@code @SpringBootTest}가 증명한다 — 실 어댑터가 기본으로 뜨면 {@code AlertSender} 빈이 둘이 되어 그것들이
 * 전부 깨진다. 이 테스트는 그 반대편(opt-in하면 실제로 실 어댑터로 바뀐다)을 못박아, 조건이 조용히
 * 뒤집혀 "켰는데 여전히 스텁"이 되지 않게 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "telegram.enabled=true", "telegram.bot-token=dummy-token", "telegram.chat-id=555000",
		// 폴러가 이 테스트 도중 실제로 텔레그램을 폴하지 않게 첫 폴을 1시간 뒤로 민다(실 네트워크 금지).
		"telegram.poll-interval-ms=3600000" })
class TelegramSenderWiringTest {

	@Autowired
	AlertSender sender;
	@Autowired
	AdminNotifier adminNotifier;
	@Autowired
	ReviewCallbackRouter router;
	@Autowired
	TelegramInboundPoller poller;

	@Test
	void enabledSelectsRealTelegramSender() {
		assertThat(sender).isInstanceOf(TelegramAlertSender.class);
	}

	@Test
	void enabledSelectsRealTelegramAdminNotifier() {
		assertThat(adminNotifier).isInstanceOf(TelegramAdminNotifier.class);
	}

	@Test
	void enabledWiresInboundRouterAndPoller() {
		assertThat(router).isNotNull(); // SEC-03 라우터 + 폴러가 opt-in 시 함께 뜬다(Q-15 인바운드)
		assertThat(poller).isNotNull();
	}
}
