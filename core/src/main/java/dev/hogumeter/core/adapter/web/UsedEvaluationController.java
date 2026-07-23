package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.EvaluateListingUseCase;
import dev.hogumeter.core.application.EvaluationOutcome;
import dev.hogumeter.core.domain.used.EvaluationInput;
import dev.hogumeter.core.domain.used.EvaluationKind;
import dev.hogumeter.core.domain.used.ExtractedListing;
import dev.hogumeter.core.domain.used.PriceContext;
import dev.hogumeter.core.domain.used.RiskSignal;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** USED-04 평가기 REST(AC-12·13). 봉투 없는 리소스 직접 반환(Q-2 잠정 확정, 다른 USED 엔드포인트와 동형). */
@RestController
@RequestMapping("/api/v1/used-searches/{usedSearchId}/evaluate")
public class UsedEvaluationController {

	private final EvaluateListingUseCase useCase;

	public UsedEvaluationController(EvaluateListingUseCase useCase) {
		this.useCase = useCase;
	}

	@PostMapping
	public EvaluationResponse evaluate(@PathVariable long usedSearchId, @RequestBody EvaluationRequest req) {
		EvaluationInput input = new EvaluationInput(req.kind(), req.text(), req.title(), req.price(), req.url());
		return EvaluationResponse.from(useCase.evaluate(usedSearchId, input, req.variantId()));
	}

	public record EvaluationRequest(EvaluationKind kind, String text, String title, Long price, String url,
			Long variantId) {
	}

	/**
	 * {@code needsInput}이 있으면 나머지는 전부 null — 입력이 부족하니 그 종류로 다시 요청하라는 뜻이다.
	 * 지어낸 값(0원·빈 문자열)으로 채우지 않는다.
	 */
	public record EvaluationResponse(EvaluationKind needsInput, ListingView listing, PriceContext priceContext,
			List<RiskSignal> riskSignals) {

		static EvaluationResponse from(EvaluationOutcome outcome) {
			if (outcome.needsInput() != null) {
				return new EvaluationResponse(outcome.needsInput(), null, null, null);
			}
			return new EvaluationResponse(null, ListingView.from(outcome.listing()), outcome.priceContext(),
					outcome.riskSignals());
		}
	}

	public record ListingView(String title, long price, String url) {

		static ListingView from(ExtractedListing listing) {
			return new ListingView(listing.title(), listing.price(), listing.url());
		}
	}
}
