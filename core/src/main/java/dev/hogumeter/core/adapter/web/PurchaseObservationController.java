package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetPurchaseObservationsUseCase;
import dev.hogumeter.core.application.PurchaseObservation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** PUR-05 관찰 문맥 조회 REST — variant의 구매별 1줄 문맥. */
@RestController
public class PurchaseObservationController {

	private final GetPurchaseObservationsUseCase getObservations;

	public PurchaseObservationController(GetPurchaseObservationsUseCase getObservations) {
		this.getObservations = getObservations;
	}

	@GetMapping("/api/v1/variants/{variantId}/purchases")
	public List<PurchaseObservation> purchases(@PathVariable long variantId) {
		return getObservations.forVariant(variantId);
	}
}
