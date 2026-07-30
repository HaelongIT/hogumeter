package dev.hogumeter.core.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** AL-03 후속 알림 자격 — VERIFIED/PRICE_CHANGED/ENDED/REOPENED는 "알림이 나갔던 딜"에 한정. */
class FollowUpEvaluatorTest {

	private final FollowUpEvaluator evaluator = new FollowUpEvaluator();

	@ParameterizedTest
	@EnumSource(value = FollowUpKind.class, names = "PINNED_PRICE_INCREASED", mode = EnumSource.Mode.EXCLUDE)
	void followUpOnlyForDealsThatAlreadyAlerted(FollowUpKind kind) {
		assertThat(evaluator.shouldSendFollowUp(kind, true)).isTrue();
		assertThat(evaluator.shouldSendFollowUp(kind, false)).isFalse();
	}

	/** Q-83 ④(2026-07-30 확정) — 핀 자체가 자격이다. 첫 알림 여부와 무관하게 항상 허용한다. */
	@Test
	void pinnedPriceIncreaseIgnoresTheAlreadyAlertedGate() {
		assertThat(evaluator.shouldSendFollowUp(FollowUpKind.PINNED_PRICE_INCREASED, true)).isTrue();
		assertThat(evaluator.shouldSendFollowUp(FollowUpKind.PINNED_PRICE_INCREASED, false)).isTrue();
	}
}
