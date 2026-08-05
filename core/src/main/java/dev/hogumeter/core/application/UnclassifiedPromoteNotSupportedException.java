package dev.hogumeter.core.application;

/**
 * 승격이 지원되지 않는 경우(Q-15). 두 가지다 — ① 미상(UNCLASSIFIED) 항목은 딜이 없어, 승격하려면 사람이
 * <b>어느 variant인지</b> 골라야 한다({@code variantId} 없이는 여전히 거절 — 지어내지 않는다, 과대약속 금지.
 * {@code variantId}가 있으면 2026-07-31부터 지원한다, {@link ResolveReviewItemUseCase}). ② DEMAND_UNKNOWN·
 * KEYWORD_SUGGEST 유형은 승격 개념 자체가 없다(딜이 이미 있거나 애초에 딜 대상이 아니다) — 기각만 된다.
 * 이상치(OUTLIER_LOWER)는 딜이 이미 있어 승격·기각 둘 다 된다.
 */
public class UnclassifiedPromoteNotSupportedException extends RuntimeException {

	public static final String CODE = "REVIEW_PROMOTE_UNSUPPORTED";

	public UnclassifiedPromoteNotSupportedException(long reviewItemId) {
		super("이 항목은 승격할 수 없습니다(variant 미지정 또는 승격 불가 유형): #" + reviewItemId);
	}
}
