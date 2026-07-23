package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.used.EvaluationKind;
import dev.hogumeter.core.domain.used.ExtractedListing;
import dev.hogumeter.core.domain.used.PriceContext;
import dev.hogumeter.core.domain.used.RiskSignal;
import java.util.List;

/**
 * USED-04 평가 결과(AC-12·13). 두 모양 중 하나다:
 * <ul>
 *   <li>{@code needsInput != null} — 이 입력으로는 못 읽었다. 다음 폴백 단계를 가리킨다(URL→TEXT→MANUAL).
 *       나머지 필드는 전부 null이다.</li>
 *   <li>{@code listing != null} — 구조화에 성공했다. 가격 맥락·위험 신호까지 채워진다.</li>
 * </ul>
 * 하나의 record로 두 모양을 담는 이유: 이 프로젝트의 다른 View들(SignalView 등)도 상태를 sealed
 * interface가 아니라 nullable 필드 조합으로 표현한다 — 일관성.
 */
public record EvaluationOutcome(ExtractedListing listing, PriceContext priceContext, List<RiskSignal> riskSignals,
		EvaluationKind needsInput) {

	public static EvaluationOutcome needsInput(EvaluationKind nextKind) {
		return new EvaluationOutcome(null, null, null, nextKind);
	}

	public static EvaluationOutcome resolved(ExtractedListing listing, PriceContext priceContext,
			List<RiskSignal> riskSignals) {
		return new EvaluationOutcome(listing, priceContext, List.copyOf(riskSignals), null);
	}
}
