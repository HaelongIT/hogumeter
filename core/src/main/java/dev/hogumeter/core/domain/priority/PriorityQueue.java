package dev.hogumeter.core.domain.priority;

/**
 * PRI ②축소(docs/19, DN-P) — Product 단위 "대기" 판정(순수). 대기 = 아직 손 안 댄 제품이다.
 * Purchase가 하나라도 있으면(구매됨/완료·재활성 가능한 archived 포함) 더는 대기가 아니고, 사용자가
 * "수동 완료"(중고·장외 구매 이탈용, 취소 가능)로 표시해도 대기가 아니다.
 *
 * <p><b>단순화(docs/91 Q-82)</b>: docs/19 원문은 "ARCHIVED 아님"과 "활성 구매 없음"을 별개 조건으로
 * 적고, 비대기 상태의 표시도 갈랐다("구매됨/완료"는 목록 배지, ARCHIVED는 설정 화면만). 이 함수는 그
 * 표시 차이를 가르지 않고 "Purchase가 하나라도 있는가"만 본다 — 대기 여부라는 데이터 진실은 하나로
 * 충분하고, 배지 문구·숨김 위치는 표시 손잡이(절대 원칙 4)라 web 착수 시 화면이 Purchase 상태별로 가른다.
 */
public final class PriorityQueue {

	private PriorityQueue() {
	}

	public static boolean isWaiting(boolean hasAnyPurchase, boolean manuallyCompleted) {
		return !hasAnyPurchase && !manuallyCompleted;
	}
}
