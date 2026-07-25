package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetPrioritizedProductsUseCase;
import dev.hogumeter.core.application.SetProductManuallyCompletedUseCase;
import dev.hogumeter.core.application.SetProductPriorityUseCase;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/** PRI ②축소(docs/19) REST — 목록 정렬 1곳 + 순번·수동 완료 손잡이. 봉투 없는 리소스 직접 반환(Q-2). */
@RestController
@RequestMapping("/api/v1")
public class PriorityController {

	private final GetPrioritizedProductsUseCase getPrioritized;
	private final SetProductPriorityUseCase setPriority;
	private final SetProductManuallyCompletedUseCase setManuallyCompleted;

	public PriorityController(GetPrioritizedProductsUseCase getPrioritized, SetProductPriorityUseCase setPriority,
			SetProductManuallyCompletedUseCase setManuallyCompleted) {
		this.getPrioritized = getPrioritized;
		this.setPriority = setPriority;
		this.setManuallyCompleted = setManuallyCompleted;
	}

	@GetMapping("/products/prioritized")
	public List<GetPrioritizedProductsUseCase.PrioritizedProduct> prioritized() {
		return getPrioritized.list();
	}

	@PutMapping("/products/{productId}/priority")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setPriority(@PathVariable long productId, @RequestBody SetPriorityRequest request) {
		setPriority.setPriority(productId, request.rank());
	}

	@PutMapping("/products/{productId}/manually-completed")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setManuallyCompleted(@PathVariable long productId, @RequestBody SetManuallyCompletedRequest request) {
		setManuallyCompleted.set(productId, request.manuallyCompleted());
	}

	/** {@code rank}가 {@code null}이면 순번 해제(미지정으로 되돌림). */
	public record SetPriorityRequest(Integer rank) {
	}

	public record SetManuallyCompletedRequest(boolean manuallyCompleted) {
	}
}
