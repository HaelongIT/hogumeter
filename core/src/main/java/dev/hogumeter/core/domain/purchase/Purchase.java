package dev.hogumeter.core.domain.purchase;

import java.time.Duration;
import java.time.Instant;

/**
 * PUR-01 구매 기록·관찰(순수 값). variant 정책을 점유하지 않아 variant당 복수 공존 가능(독립 관찰).
 * 신품 한정. purchasedAt은 발생 시각(날짜만 입력이면 23:59 KST 보정은 입력 계층 책임).
 *
 * @param demandAxisValue SPLIT 필수·GROUPED 선택(null 허용)
 * @param paidPrice as-paid 실지불가
 * @param observationDays 관찰 기간(기본 90, 자동확장 없음)
 * @param linkedDealEventId 연결 딜(재구성 시 null + 이력) — 선택
 */
public record Purchase(long variantId, String demandAxisValue, long paidPrice, Instant purchasedAt,
		int observationDays, Long linkedDealEventId, PurchaseState state) {

	/** 관찰 시작(OBSERVING) 구매 생성. observationDays 기본값은 호출 계층이 적용(가안 90). */
	public static Purchase observing(long variantId, String demandAxisValue, long paidPrice,
			Instant purchasedAt, int observationDays) {
		return new Purchase(variantId, demandAxisValue, paidPrice, purchasedAt, observationDays, null,
				PurchaseState.OBSERVING);
	}

	/** 관찰 만료 → 성적 집계 대기. */
	public Purchase expire() {
		return withState(state.transitionTo(PurchaseState.REPORT_PENDING));
	}

	/** 성적표 발급 → 종료. */
	public Purchase close() {
		return withState(state.transitionTo(PurchaseState.CLOSED));
	}

	/** 아카이브(다른 활성 관찰 없을 때 — 조건 판정은 use-case). */
	public Purchase archive() {
		return withState(state.transitionTo(PurchaseState.ARCHIVED));
	}

	/** 재활성(수동 복원·재구매). */
	public Purchase reactivate() {
		return withState(state.transitionTo(PurchaseState.OBSERVING));
	}

	public Instant observationEndsAt() {
		return purchasedAt.plus(Duration.ofDays(observationDays));
	}

	public boolean isExpired(Instant now) {
		return !now.isBefore(observationEndsAt());
	}

	private Purchase withState(PurchaseState newState) {
		return new Purchase(variantId, demandAxisValue, paidPrice, purchasedAt, observationDays, linkedDealEventId,
				newState);
	}
}
