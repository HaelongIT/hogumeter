package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetSignalUseCase;
import dev.hogumeter.core.domain.signal.SignalView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** SIG 신호등 조회 REST(docs/16). 표시 전용 — 알림 트리거 아님. */
@RestController
public class SignalController {

	private final GetSignalUseCase getSignal;

	public SignalController(GetSignalUseCase getSignal) {
		this.getSignal = getSignal;
	}

	@GetMapping("/api/v1/variants/{variantId}/signal")
	public SignalView signal(@PathVariable long variantId) {
		return getSignal.getSignal(variantId);
	}
}
