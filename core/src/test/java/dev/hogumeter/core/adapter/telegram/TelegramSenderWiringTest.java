package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.TestcontainersConfiguration;
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
@SpringBootTest(properties = { "telegram.enabled=true", "telegram.bot-token=dummy-token", "telegram.chat-id=555000" })
class TelegramSenderWiringTest {

	@Autowired
	AlertSender sender;

	@Test
	void enabledSelectsRealTelegramSender() {
		assertThat(sender).isInstanceOf(TelegramAlertSender.class);
	}
}
