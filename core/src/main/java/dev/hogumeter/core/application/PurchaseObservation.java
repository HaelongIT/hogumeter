package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.purchase.ObservationContext;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import dev.hogumeter.core.domain.purchase.ReportCard;
import java.time.Instant;

/**
 * PUR-05 조회 뷰 — 저장된 Purchase + 산출된 관찰 문맥(1줄).
 *
 * <p>{@code reportCard}는 CLOSED 구매의 발급된 성적표(PUR-04)다. 그 외 상태(OBSERVING·REPORT_PENDING)에선
 * null이다 — 아직 발급되지 않았음을 그대로 노출한다(정직성). 이 필드가 report_card를 <b>읽는 유일한 소비처</b>다:
 * 없으면 발급은 쓰기만 하고 아무도 못 보는 write-only 테이블이 된다(쌓이는데 볼 수가 없던 review_queue_item의 거울상).
 */
public record PurchaseObservation(
		long purchaseId,
		PurchaseState state,
		long paidPrice,
		Instant purchasedAt,
		ObservationContext context,
		ReportCard reportCard) {
}
