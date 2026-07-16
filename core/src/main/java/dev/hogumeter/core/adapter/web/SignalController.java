package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetSignalUseCase;
import dev.hogumeter.core.domain.signal.SignalView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SIG 신호등 조회 REST(docs/16). 표시 전용 — 알림 트리거 아님. */
@RestController
public class SignalController {

	private final GetSignalUseCase getSignal;

	public SignalController(GetSignalUseCase getSignal) {
		this.getSignal = getSignal;
	}

	/**
	 * @param demandAxisValue 분리 제품에서 볼 수요축 값(Q-66 ①). 기준가와 <b>같은 표본</b>을 봐야 한다 —
	 *     한쪽만 색을 가르면 같은 화면이 서로 다른 사실을 말한다.
	 */
	@GetMapping("/api/v1/variants/{variantId}/signal")
	public SignalView signal(@PathVariable long variantId,
			@RequestParam(required = false) String demandAxisValue) {
		return getSignal.getSignal(variantId, demandAxisValue);
	}
}
