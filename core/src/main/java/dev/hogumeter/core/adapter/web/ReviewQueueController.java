package dev.hogumeter.core.adapter.web;

import dev.hogumeter.core.application.GetReviewQueueUseCase;
import dev.hogumeter.core.application.GetReviewQueueUseCase.PendingItem;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미상 큐 조회 REST(읽기 전용). 봉투 없는 리소스 직접 반환(Q-2 확정). 빈 큐는 빈 배열이다 — 404가 아니다.
 *
 * <p><b>승격·기각(쓰기)은 없다.</b> {@code DealEventEntity}에 승격/기각 전이 메서드가 없고
 * ({@code DealEvent.promoteFromOutlier()}·{@code reject()}는 순수 도메인에만 있고 프로덕션 호출자가 없다),
 * {@code ReviewQueueItemEntity}가 {@code status} 컬럼을 매핑하지 않아 "처리됨"을 기록할 수 없다.
 * 둘 다 다른 개발자 소유 파일이다 — docs/91 Q-15에 조건을 적어 두고 여기선 읽기만 한다.
 */
@RestController
public class ReviewQueueController {

	private final GetReviewQueueUseCase reviewQueue;

	public ReviewQueueController(GetReviewQueueUseCase reviewQueue) {
		this.reviewQueue = reviewQueue;
	}

	@GetMapping("/api/v1/review-queue")
	public List<PendingItem> pending() {
		return reviewQueue.pending();
	}
}
