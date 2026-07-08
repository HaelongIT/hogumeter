package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetCadenceUseCase;
import dev.hogumeter.core.domain.cadence.CadenceView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CAD 딜 주기 조회 REST(docs/16). 예측 없음 — 발생·간격·경과일만. */
@RestController
public class CadenceController {

	private final GetCadenceUseCase getCadence;

	public CadenceController(GetCadenceUseCase getCadence) {
		this.getCadence = getCadence;
	}

	@GetMapping("/api/v1/variants/{variantId}/cadence")
	public CadenceView cadence(@PathVariable long variantId,
			@RequestParam(defaultValue = "6") int periodMonths) {
		return getCadence.getCadence(variantId, periodMonths);
	}
}
