package dev.hogumeter.core.application;

/**
 * 미상(UNCLASSIFIED) 항목의 승격은 아직 지원하지 않는다(Q-15). 미상 항목은 딜이 없어, 승격하려면 사람이
 * <b>어느 variant인지 골라야</b> 하는데 그 입력 경로가 없다 — 지어내지 않는다(과대약속 금지). 기각은 된다
 * (딜을 만들지 않고 큐에서 내린다). 이상치(OUTLIER_LOWER)는 딜이 이미 있어 승격·기각 둘 다 된다.
 */
public class UnclassifiedPromoteNotSupportedException extends RuntimeException {

	public static final String CODE = "REVIEW_PROMOTE_UNSUPPORTED";

	public UnclassifiedPromoteNotSupportedException(long reviewItemId) {
		super("미상 항목은 승격하려면 variant를 지정해야 합니다(미구현): #" + reviewItemId);
	}
}
