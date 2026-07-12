package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetReviewQueueUseCase;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import dev.hogumeter.core.application.ResolveReviewItemUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미상 큐 REST. 봉투 없는 리소스 직접 반환(Q-2 확정). 빈 큐는 빈 배열이다 — 404가 아니다.
 *
 * <p><b>승격·기각(Q-15)</b>: 이상치 오탐을 정상으로 되돌리거나(promote) 사기·낚시로 영구 제외한다(reject).
 * 판단은 순수 도메인({@code DealEvent.promoteFromOutlier()}·{@code reject()})이 하고, 처리된 항목은
 * {@code status}·{@code resolved_at}·{@code channel}로 큐에서 내린다. 없는/이미 처리된 항목은 404.
 * 미상 항목 승격은 variant 지정이 필요해 아직 막는다(400) — 기각은 된다.
 */
@RestController
public class ReviewQueueController {

	private final GetReviewQueueUseCase reviewQueue;
	private final ResolveReviewItemUseCase resolve;

	public ReviewQueueController(GetReviewQueueUseCase reviewQueue, ResolveReviewItemUseCase resolve) {
		this.reviewQueue = reviewQueue;
		this.resolve = resolve;
	}

	@GetMapping("/api/v1/review-queue")
	public List<PendingItem> pending() {
		return reviewQueue.pending();
	}

	@PostMapping("/api/v1/review-queue/{id}/promote")
	public void promote(@PathVariable long id) {
		resolve.promote(id);
	}

	@PostMapping("/api/v1/review-queue/{id}/reject")
	public void reject(@PathVariable long id) {
		resolve.reject(id);
	}
}
