package dev.hogumeter.core.domain.purchase;

/**
 * PUR-04 성적표 발급 전제(순수). 관찰 만료 AND 배치 완료 AND 미분류 유예 종결(48h)일 때만 발급 → quiet 발송(관통 없음).
 * 재발급 없음. batchComplete·holdResolved는 외부 상태(백필 배치·미처리 큐)에서 판정해 주입.
 */
public final class ReportIssueGate {

	private ReportIssueGate() {
	}

	public static boolean canIssue(boolean expired, boolean batchComplete, boolean unclassifiedHoldResolved) {
		return expired && batchComplete && unclassifiedHoldResolved;
	}
}
