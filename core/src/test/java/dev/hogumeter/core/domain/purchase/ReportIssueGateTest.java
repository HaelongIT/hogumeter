package dev.hogumeter.core.domain.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** PUR-04 발급 전제 — 만료 ∧ 배치 완료 ∧ 미분류 유예 종결. 하나라도 미충족이면 발급 불가. */
class ReportIssueGateTest {

	@ParameterizedTest(name = "expired={0} batch={1} hold={2} → issue={3}")
	@CsvSource({
			"true, true, true, true",
			"false, true, true, false",
			"true, false, true, false",
			"true, true, false, false"
	})
	void issueRequiresAllThreePreconditions(boolean expired, boolean batch, boolean hold, boolean canIssue) {
		assertThat(ReportIssueGate.canIssue(expired, batch, hold)).isEqualTo(canIssue);
	}
}
