package dev.hogumeter.core.application;

import dev.hogumeter.core.application.ComputeDigestBestOpportunityUseCase.BestOpportunity;
import dev.hogumeter.core.application.ComputeDigestOpportunityCountUseCase.OpportunityCount;
import dev.hogumeter.core.application.ComputeDigestTransitionUseCase.DigestTransition;
import dev.hogumeter.core.domain.digest.DigestWindow;
import dev.hogumeter.core.domain.digest.PinDigestEvent;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * DIG-04 조립(docs/18) — 한 variant의 다이제스트 행을 만든다. 섹션별 유스케이스(창·① 딜 요약·③ 관찰
 * 경과·② 전환)를 <b>같은 창</b>으로 묶어 한 번에 낸다. 여태 만든 생산자들이 실제로 맞물리는지 검사하는
 * 첫 소비자다(각각은 테스트만 불렀다 — "소비자를 만드는 일은 생산자를 검사하는 일이다").
 *
 * <p>이 유스케이스는 <b>순수 조립</b>이다 — 렌더링(문구·아이콘)도, 발송도, 저장물 갱신도 하지 않는다.
 * 그 셋은 발송 단계의 몫이다(docs/91 Q-81). 여기서 창을 한 번 구해 세 섹션에 같은 창을 주는 것이 계약의
 * 핵심이다: 섹션마다 창을 따로 구하면 그 사이 시각이 흘러 "① 최고 기회"와 "③ 관찰 경과"가 서로 다른
 * 창을 볼 수 있다.
 */
@Service
public class AssembleVariantDigestUseCase {

	private final ComputeDigestWindowUseCase windows;
	private final ComputeDigestBestOpportunityUseCase bestOpportunities;
	private final ComputeDigestOpportunityCountUseCase opportunityCounts;
	private final ComputeDigestTransitionUseCase transitions;
	private final ComputeDigestPinEndingsUseCase pinEndings;

	public AssembleVariantDigestUseCase(ComputeDigestWindowUseCase windows,
			ComputeDigestBestOpportunityUseCase bestOpportunities,
			ComputeDigestOpportunityCountUseCase opportunityCounts, ComputeDigestTransitionUseCase transitions,
			ComputeDigestPinEndingsUseCase pinEndings) {
		this.windows = windows;
		this.bestOpportunities = bestOpportunities;
		this.opportunityCounts = opportunityCounts;
		this.transitions = transitions;
		this.pinEndings = pinEndings;
	}

	public VariantDigestRow assemble(long variantId) {
		DigestWindow window = windows.window(variantId);
		return new VariantDigestRow(
				variantId,
				window,
				bestOpportunities.bestOpportunity(variantId, window),
				opportunityCounts.count(variantId, window),
				transitions.transition(variantId),
				pinEndings.pinEvents(variantId, window));
	}

	/**
	 * 한 variant의 다이제스트 행. 렌더링이 이 값들을 섹션 문구로 옮긴다.
	 *
	 * @param bestOpportunity ① 이번 창 최고 기회(없으면 empty — 그리지 않는다)
	 * @param observation ③ 관찰 경과(이번 창 +k / 누적 N)
	 * @param transition ② 전환(from/to 색 + 보고 대상 여부)
	 * @param pinEvents ④ 핀 결말 전이 + 부활 이벤트(기각 제외, 없으면 빈 리스트)
	 */
	public record VariantDigestRow(long variantId, DigestWindow window, Optional<BestOpportunity> bestOpportunity,
			OpportunityCount observation, DigestTransition transition, List<PinDigestEvent> pinEvents) {
	}
}
