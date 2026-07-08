package dev.hogumeter.core.domain.digest;

import dev.hogumeter.core.domain.signal.SignalColor;

/**
 * DIGEST 순수 규칙(docs/18). ② 전환은 신호 <b>색 변화</b>만 보고 — 색이 같으면 관찰 문맥·basis 모드가
 * 바뀌어도 억제(전환 억제 신호). ⑥ 조용한 주 = 전 플로우 0 ∧ 전환 0 ∧ 관측 공백 없음.
 */
public final class DigestRules {

	private DigestRules() {
	}

	public static boolean isReportableTransition(SignalColor stored, SignalColor current) {
		return stored != current;
	}

	public static boolean isQuietWeek(boolean anyFlow, boolean anyTransition, boolean anyGap) {
		return !anyFlow && !anyTransition && !anyGap;
	}
}
