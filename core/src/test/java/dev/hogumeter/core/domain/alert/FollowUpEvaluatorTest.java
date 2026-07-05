package dev.hogumeter.core.domain.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** AL-03 후속 알림 자격 — VERIFIED/PRICE_CHANGED/ENDED는 "알림이 나갔던 딜"에 한정. */
class FollowUpEvaluatorTest {

	private final FollowUpEvaluator evaluator = new FollowUpEvaluator();

	@ParameterizedTest
	@EnumSource(FollowUpKind.class)
	void followUpOnlyForDealsThatAlreadyAlerted(FollowUpKind kind) {
		assertThat(evaluator.shouldSendFollowUp(kind, true)).isTrue();
		assertThat(evaluator.shouldSendFollowUp(kind, false)).isFalse();
	}
}
