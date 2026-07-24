package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.digest.DigestRules;
import dev.hogumeter.core.domain.digest.DigestWindow;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * DIG-04 ⑥ 조용한 주(docs/18) — 전 플로우 0 ∧ 전환 0 ∧ 관측 공백 없음. {@link DigestRules#isQuietWeek}
 * (호출자 0이던 순수 규칙)의 첫 소비자다.
 *
 * <p><b>다이제스트 전체(여러 variant)</b>에 대한 판정이다: 어느 variant에도 이번 창 플로우(occurrenceSet
 * 가시화 딜)가 없고, 어느 variant에도 신호 전환이 없고, 관측 공백도 없으면 "조용한 주"다. 각 variant는
 * 자기 창을 갖는다(DIG-03) — 그래서 {@link ComputeDigestWindowUseCase}로 variant별 창을 먼저 구하고,
 * 그 창에 대해 플로우·전환을 묻는다.
 *
 * <p><b>관측 공백(anyGap)은 지금 항상 false다</b>(2026-07-25 결정, docs/91 Q-81). "공백 없음"을 고정으로
 * 두고 관측시계(SIG-02 {@code site_poll_state}) 연결은 후속으로 미룬다 — {@link #ANY_GAP}가 그 seam이다.
 * <b>한계</b>: 수집이 실제로 멈춘 주에도 "조용한 주"로 보고될 수 있다. 관측시계를 연결하면 이 상수를
 * 실제 판정으로 바꾸는 한 곳만 고치면 된다.
 */
@Service
public class ComputeDigestQuietWeekUseCase {

	/** 결정(Q-81): 관측 공백 판정은 후속. 지금은 "공백 없음" 고정 — 여기 한 곳이 seam이다. */
	private static final boolean ANY_GAP = false;

	private final ComputeDigestWindowUseCase windows;
	private final ComputeDigestOpportunityCountUseCase opportunities;
	private final ComputeDigestTransitionUseCase transitions;

	public ComputeDigestQuietWeekUseCase(ComputeDigestWindowUseCase windows,
			ComputeDigestOpportunityCountUseCase opportunities, ComputeDigestTransitionUseCase transitions) {
		this.windows = windows;
		this.opportunities = opportunities;
		this.transitions = transitions;
	}

	public boolean isQuietWeek(List<Long> variantIds) {
		boolean anyFlow = false;
		boolean anyTransition = false;
		for (long variantId : variantIds) {
			DigestWindow window = windows.window(variantId);
			if (opportunities.count(variantId, window).inWindow() > 0) {
				anyFlow = true;
			}
			if (transitions.transition(variantId).reportable()) {
				anyTransition = true;
			}
		}
		return DigestRules.isQuietWeek(anyFlow, anyTransition, ANY_GAP);
	}
}
