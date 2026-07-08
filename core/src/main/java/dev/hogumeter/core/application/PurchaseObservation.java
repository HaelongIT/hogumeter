package dev.hogumeter.core.application;

import dev.hogumeter.core.domain.purchase.ObservationContext;
import dev.hogumeter.core.domain.purchase.PurchaseState;
import java.time.Instant;

/** PUR-05 조회 뷰 — 저장된 Purchase + 산출된 관찰 문맥(1줄). */
public record PurchaseObservation(
		long purchaseId,
		PurchaseState state,
		long paidPrice,
		Instant purchasedAt,
		ObservationContext context) {
}
