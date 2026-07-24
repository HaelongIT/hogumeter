package dev.hogumeter.core.adapter.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 발송 스텁의 순수 계약 — 실전송이 없어 실패할 수 없으므로 DIGEST 스텁은 항상 성공을 보고한다. */
class StubAlertSenderTest {

	@Test
	void sendDigestAlwaysReportsSuccess() {
		assertThat(new StubAlertSender().sendDigest("다이제스트 본문")).isTrue();
	}
}
