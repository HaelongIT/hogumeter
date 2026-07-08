package dev.hogumeter.core.domain.digest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hogumeter.core.domain.signal.SignalColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** DIG-04 ② 전환 억제(색 변화만 보고) · ⑥ 조용한 주(플로우 0 ∧ 전환 0 ∧ 공백 없음). */
class DigestRulesTest {

	@Test
	void colorChangeIsReportableTransition() {
		assertThat(DigestRules.isReportableTransition(SignalColor.GREEN, SignalColor.RED)).isTrue();
	}

	@Test
	void sameColorIsSuppressedEvenIfOtherComponentsChanged() {
		// 색 동일 → 관찰 문맥·basis 모드가 바뀌어도 전환 미보고(억제 신호)
		assertThat(DigestRules.isReportableTransition(SignalColor.GREEN, SignalColor.GREEN)).isFalse();
	}

	// 조용한 주 = 전 플로우 0 ∧ 전환 0 ∧ 공백 없음
	@ParameterizedTest(name = "flow={0} transition={1} gap={2} → quiet={3}")
	@CsvSource({
			"false, false, false, true",
			"true, false, false, false",
			"false, true, false, false",
			"false, false, true, false"
	})
	void quietWeekRequiresNoFlowNoTransitionNoGap(boolean anyFlow, boolean anyTransition, boolean anyGap,
			boolean quiet) {
		assertThat(DigestRules.isQuietWeek(anyFlow, anyTransition, anyGap)).isEqualTo(quiet);
	}
}
