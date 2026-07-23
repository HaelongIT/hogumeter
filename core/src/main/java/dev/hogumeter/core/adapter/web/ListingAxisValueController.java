package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.PromoteAxisValueUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** USED-05 AC-17 ② 메모 값을 축으로 승격하는 REST — 명시적 사용자 행위. */
@RestController
@RequestMapping("/api/v1/listings/{listingId}/axis-values")
public class ListingAxisValueController {

	private final PromoteAxisValueUseCase useCase;

	public ListingAxisValueController(PromoteAxisValueUseCase useCase) {
		this.useCase = useCase;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void promote(@PathVariable long listingId, @RequestBody AxisValueRequest req) {
		useCase.promote(listingId, req.axisId(), req.value());
	}

	public record AxisValueRequest(long axisId, String value) {
	}
}
